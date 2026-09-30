import {initAudit,refreshAudit,clearAudit} from './audit.js';
const $ = (id) => document.getElementById(id);
const number = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });
const precise = new Intl.NumberFormat("vi-VN", {
  maximumSignificantDigits: 10,
});
let token = "",
  connection = null,
  timer = null,
  refreshing = false,
  names = [],
  samples = [],
  detailVersion = 0;
const selectedTags = new Map();
function element(tag, text) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  return node;
}
function message(text = "") {
  $("metrics-notice").textContent = text;
  $("metrics-notice").hidden = !text;
}
function state(text, status) {
  $("connection").textContent = text;
  $("connection").dataset.state = status;
}
function disconnect(note = "") {
  clearAudit();
  connection?.abort();
  connection = null;
  token = "";
  clearInterval(timer);
  timer = null;
  refreshing = false;
  detailVersion++;
  $("ops-token").value = "";
  $("monitor").hidden = true;
  $("connect-panel").hidden = false;
  $("connect-button").disabled = false;
  for (const id of [
    "health-value",
    "memory-value",
    "cpu-value",
    "uptime-value",
    "http-count",
    "http-latency",
    "threads-value",
    "db-active",
    "db-pending",
    "mail-pending",
    "mail-sending",
    "mail-sent",
    "mail-failed",
  ])
    $(id).textContent = "—";
  $("metric-select").replaceChildren();
  $("metric-tags").replaceChildren();
  $("metric-values").replaceChildren();
  $("metric-description").textContent = "";
  $("memory-line").setAttribute("points", "");
  $("memory-dot").setAttribute("hidden", "");
  names = [];
  samples = [];
  selectedTags.clear();
  $("metric-search").value = "";
  $("updated").textContent = "Chưa có dữ liệu";
  $("chart-caption").textContent =
    "Đồ thị bắt đầu từ khi kết nối. Tối đa 30 mẫu, không lưu lịch sử sau khi tải lại trang.";
  state("Chưa kết nối", "");
  message(note);
}
async function request(path, session, allowUnavailable = false, base = "/actuator/") {
  const response = await fetch(base + path, {
    headers: { Authorization: "Bearer " + token },
    credentials: "omit",
    cache: "no-store",
    signal: AbortSignal.any([session.signal, AbortSignal.timeout(12000)]),
  });
  if (response.status === 401 || response.status === 403) {
    if (connection === session)
      disconnect(
        "Token không hợp lệ hoặc máy chủ chưa cấu hình OPS_TOKEN. Vui lòng kiểm tra và kết nối lại.",
      );
    throw new Error("Không có quyền truy cập.");
  }
  if (!response.ok && !(allowUnavailable && response.status === 503))
    throw new Error("Không tải được dữ liệu (" + response.status + ").");
  return response.json();
}
function value(data, stat = "VALUE") {
  const result = data?.measurements?.find((m) => m.statistic === stat)?.value;
  return typeof result === "number" && Number.isFinite(result) ? result : null;
}
function textValue(id, value, unit = "") {
  $(id).textContent = value === null ? "—" : number.format(value) + unit;
}
function duration(seconds) {
  if (seconds === null) return "—";
  const total = Math.max(0, Math.floor(seconds));
  return (
    (total >= 86400 ? Math.floor(total / 86400) + " ngày " : "") +
    Math.floor((total % 86400) / 3600) +
    " giờ " +
    Math.floor((total % 3600) / 60) +
    " phút"
  );
}
function chart(memory) {
  if (memory === null) return;
  samples.push({ time: new Date(), value: memory / 1048576 });
  samples = samples.slice(-30);
  const values = samples.map((s) => s.value),
    min = Math.min(...values),
    max = Math.max(...values),
    padding = Math.max((max - min) * 0.15, 1),
    low = Math.max(0, min - padding),
    high = max + padding;
  const points = samples.map((s, i) => [
    8 + (i * 584) / Math.max(1, samples.length - 1),
    150 - ((s.value - low) * 140) / (high - low),
  ]);
  $("memory-line").setAttribute(
    "points",
    points.map((p) => p.join(",")).join(" "),
  );
  const last = points.at(-1);
  $("memory-dot").setAttribute("cx", last[0]);
  $("memory-dot").setAttribute("cy", last[1]);
  $("memory-dot").removeAttribute("hidden");
  $("chart-caption").textContent =
    `${samples.length}/30 mẫu · ${samples[0].time.toLocaleTimeString("vi-VN")} – ${samples.at(-1).time.toLocaleTimeString("vi-VN")} · Thấp nhất ${number.format(min)} MiB / cao nhất ${number.format(max)} MiB. Chỉ lưu trong lần kết nối này.`;
}
async function refresh() {
  if (!connection || refreshing) return;
  refreshing = true;
  const session = connection;
  $("refresh-metrics").disabled = true;
  try {
    const queries = {
      health: "health",
      memory: "jvm.memory.used",
      cpu: "process.cpu.usage",
      uptime: "process.uptime",
      http: "http.server.requests",
      threads: "jvm.threads.live",
      active: "hikaricp.connections.active",
      pending: "hikaricp.connections.pending",
    };
    for (const status of ["PENDING", "SENDING", "SENT", "FAILED"])
      queries["mail" + status] = "linkhub.mail.jobs?tag=status:" + status;
    const entries = Object.entries(queries),
      results = await Promise.allSettled(
        entries.map(([key, name]) =>
          key === "health"
            ? request("health", session, true)
            : names.includes(name.split("?")[0])
              ? request("metrics/" + name, session)
              : Promise.resolve(null),
        ),
      );
    if (connection !== session) return;
    const data = Object.fromEntries(
      entries.map(([key], i) => [
        key,
        results[i].status === "fulfilled" ? results[i].value : null,
      ]),
    );
    const failed = results.filter((r) => r.status === "rejected").length;
    $("health-value").textContent =
      data.health?.status === "UP"
        ? "Hoạt động"
        : data.health?.status === "DOWN"
          ? "Có sự cố"
          : data.health?.status || "—";
    $("health-value").dataset.state =
      data.health?.status === "UP" ? "ok" : "error";
    textValue(
      "memory-value",
      value(data.memory) === null ? null : value(data.memory) / 1048576,
      " MiB",
    );
    textValue(
      "cpu-value",
      value(data.cpu) === null || value(data.cpu) < 0
        ? null
        : value(data.cpu) * 100,
      "%",
    );
    $("uptime-value").textContent = duration(value(data.uptime));
    const count = value(data.http, "COUNT"),
      total = value(data.http, "TOTAL_TIME");
    textValue("http-count", count);
    textValue(
      "http-latency",
      count > 0 && total !== null ? (total / count) * 1000 : null,
      " ms",
    );
    textValue("threads-value", value(data.threads));
    textValue("db-active", value(data.active));
    textValue("db-pending", value(data.pending));
    for (const status of ["PENDING", "SENDING", "SENT", "FAILED"])
      textValue("mail-" + status.toLowerCase(), value(data["mail" + status]));
    const mailFailed = value(data.mailFAILED);
    $("mail-summary").textContent =
      mailFailed === null
        ? "Chưa có dữ liệu"
        : mailFailed > 0
          ? "Có email thất bại"
          : "Không có email thất bại";
    $("mail-summary").dataset.state =
      mailFailed === null ? "" : mailFailed > 0 ? "error" : "ok";
    chart(value(data.memory));
    await refreshAudit();
    if (connection !== session) return;
    $("updated").textContent =
      "Lấy mẫu lúc " + new Date().toLocaleTimeString("vi-VN");
    state(
      failed ? "Dữ liệu chưa đầy đủ" : "Đã kết nối",
      failed ? "stale" : "ok",
    );
    message(
      failed
        ? "Một số số liệu chưa tải được. Dấu — biểu thị dữ liệu chưa có; hãy thử làm mới."
        : "",
    );
  } finally {
    if (connection === session) {
      refreshing = false;
      $("refresh-metrics").disabled = false;
    }
  }
}
function schedule() {
  clearInterval(timer);
  timer = null;
  if (connection && $("auto-refresh").checked)
    timer = setInterval(() => {
      if (!document.hidden)
        refresh().catch(() => message("Không thể cập nhật. Vui lòng thử lại."));
    }, 15000);
}
function populate() {
  const search = $("metric-search").value.trim().toLowerCase(),
    previous = $("metric-select").value;
  $("metric-select").replaceChildren();
  for (const name of names.filter((n) => n.toLowerCase().includes(search))) {
    const option = element("option", name);
    option.value = name;
    $("metric-select").append(option);
  }
  if ([...$("metric-select").options].some((o) => o.value === previous))
    $("metric-select").value = previous;
  $("metric-count").textContent = names.length + " metric";
}
async function detail(resetTags = true) {
  if (!connection || !$("metric-select").value) return;
  const session = connection,
    version = ++detailVersion,
    name = $("metric-select").value;
  if (resetTags) {
    selectedTags.clear();
    $("metric-tags").replaceChildren();
  }
  const params = new URLSearchParams();
  for (const [tag, v] of selectedTags)
    if (v) params.append("tag", tag + ":" + v);
  try {
    const data = await request(
      "metrics/" + encodeURIComponent(name) + (params.size ? "?" + params : ""),
      session,
    );
    if (connection !== session || version !== detailVersion) return;
    $("metric-description").textContent = data.description || name;
    $("metric-values").replaceChildren();
    for (const measurement of data.measurements || []) {
      const row = element("tr");
      row.append(
        element("td", measurement.statistic),
        element("td", precise.format(measurement.value)),
        element("td", data.baseUnit || "—"),
      );
      $("metric-values").append(row);
    }
    if (resetTags)
      for (const tag of data.availableTags || []) {
        const label = element("label", tag.tag),
          select = element("select");
        const all = element("option", "Tất cả");
        all.value = "";
        select.append(all);
        for (const v of tag.values) {
          const option = element("option", v);
          option.value = v;
          select.append(option);
        }
        select.addEventListener("change", () => {
          selectedTags.set(tag.tag, select.value);
          detail(false);
        });
        label.append(select);
        $("metric-tags").append(label);
      }
  } catch (error) {
    if (connection === session && version === detailVersion) {
      $("metric-values").replaceChildren();
      $("metric-description").textContent =
        "Không tải được metric này. Hãy chọn lại metric hoặc bộ lọc.";
    }
  }
}
$("connect-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const input = $("ops-token").value.trim();
  if (input.length < 32) {
    message("Token cần có ít nhất 32 ký tự.");
    return;
  }
  disconnect();
  token = input;
  connection = new AbortController();
  const session = connection;
  $("connect-button").disabled = true;
  state("Đang kết nối…", "stale");
  try {
    const catalog = await request("metrics", session);
    if (connection !== session) return;
    names = (catalog.names || []).slice().sort();
    populate();
    $("connect-panel").hidden = true;
    $("monitor").hidden = false;
    await refresh();
    if (connection === session) {
      schedule();
      await detail();
    }
  } catch (error) {
    if (connection === session)
      disconnect(
        "Không kết nối được máy chủ metric. Kiểm tra backend và token rồi thử lại.",
      );
  } finally {
    if (connection === session) $("connect-button").disabled = false;
  }
});
$("disconnect").addEventListener("click", () => disconnect());
$("refresh-metrics").addEventListener("click", () =>
  refresh().catch(() => message("Không thể cập nhật. Vui lòng thử lại.")),
);
$("auto-refresh").addEventListener("change", schedule);
$("metric-search").addEventListener("input", () => {
  populate();
  detailVersion++;
  $("metric-values").replaceChildren();
  $("metric-tags").replaceChildren();
  $("metric-description").textContent = "Chọn metric và bấm Xem chi tiết.";
});
$("metric-select").addEventListener("change", () => detail());
$("explore-form").addEventListener("submit", (event) => {
  event.preventDefault();
  detail();
});
initAudit(query=>connection?request('audit-logs?'+query,connection,false,'/api/ops/'):Promise.reject(new Error('Disconnected')));
window.addEventListener("pagehide", () => disconnect());
