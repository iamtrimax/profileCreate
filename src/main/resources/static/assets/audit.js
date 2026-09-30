const $=id=>document.getElementById(id);
let fetchAudit, cursor=null, next=null, history=[], generation=0, sequence=0, filters=new URLSearchParams();
const labels={ACCOUNT_REGISTER:'Đăng ký',ACCOUNT_LOGIN:'Đăng nhập',ACCOUNT_LOGOUT:'Đăng xuất',PROFILE_UPDATE:'Sửa profile',LINK_CREATE:'Thêm link',LINK_UPDATE:'Sửa link',LINK_DELETE:'Xóa link',SERVICE_CREATE:'Thêm dịch vụ',SERVICE_UPDATE:'Sửa dịch vụ',SERVICE_DELETE:'Xóa dịch vụ',SECTION_CREATE:'Thêm nội dung',SECTION_UPDATE:'Sửa nội dung',SECTION_DELETE:'Xóa nội dung',CONTACT_CREATE:'Gửi liên hệ',CONTACT_UPDATE:'Đổi trạng thái liên hệ',BOOKING_CREATE:'Đặt lịch',BOOKING_UPDATE:'Đổi trạng thái lịch',AVAILABILITY_UPDATE:'Sửa lịch rảnh',PASSWORD_RESET_REQUEST:'Yêu cầu đặt lại mật khẩu',PASSWORD_RESET_CONFIRM:'Đặt lại mật khẩu',EMAIL_VERIFY_REQUEST:'Yêu cầu xác minh email',EMAIL_VERIFY_CONFIRM:'Xác minh email',OPS_ACCESS:'Truy cập Ops'};
const outcomeLabels={SUCCESS:'Thành công',DENIED:'Bị từ chối',FAILURE:'Thất bại'};
function node(tag,text){const n=document.createElement(tag);if(text!==undefined)n.textContent=text;return n;}
function localInput(date){return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);}
function initialDates(){$('audit-from').value=localInput(new Date(Date.now()-7*86400000));$('audit-to').value='';}
function readFilters(){
  const from=$('audit-from').value,to=$('audit-to').value;
  const start=from?new Date(from):new Date(Date.now()-7*86400000),end=to?new Date(to):new Date();
  if(!Number.isFinite(start.getTime())||!Number.isFinite(end.getTime())||start>end||end-start>90*86400000)throw Error('Chọn khoảng thời gian hợp lệ, tối đa 90 ngày.');
  filters=new URLSearchParams({from:start.toISOString(),size:'20'});if(to)filters.set('to',end.toISOString());
  for(const [id,key] of [['audit-action','action'],['audit-outcome','outcome'],['audit-actor','actorId'],['audit-request','requestId']]){const value=$(id).value.trim();if(value)filters.set(key,value);}
}
export function initAudit(request){
  fetchAudit=request;
  for(const [value,label] of Object.entries(labels)){const option=node('option',label);option.value=value;$('audit-action').append(option);}
  initialDates();readFilters();
  $('audit-form').addEventListener('submit',e=>{e.preventDefault();try{readFilters();cursor=null;history=[];refreshAudit(true);}catch(error){$('audit-status').textContent=error.message;}});
  $('audit-refresh').addEventListener('click',()=>refreshAudit(true));
  $('audit-reset').addEventListener('click',()=>{$('audit-form').reset();initialDates();readFilters();cursor=null;history=[];refreshAudit(true);});
  $('audit-next').addEventListener('click',()=>{if(next!==null){history.push(cursor);cursor=next;refreshAudit(true);}});
  $('audit-prev').addEventListener('click',()=>{if(history.length){cursor=history.pop();refreshAudit(true);}});
}
export function clearAudit(){
  generation++;sequence++;cursor=null;next=null;history=[];$('audit-rows').replaceChildren();$('audit-status').textContent='';$('audit-page').textContent='';
  for(const id of ['audit-total','audit-success','audit-denied','audit-failure'])$(id).textContent='—';
  $('audit-form').reset();initialDates();readFilters();$('audit-prev').disabled=true;$('audit-next').disabled=true;
}
export async function refreshAudit(force=false){
  if(!fetchAudit||(!force&&cursor!==null))return;
  const epoch=generation,request=++sequence,query=new URLSearchParams(filters);if(cursor!==null)query.set('before',cursor);
  $('audit-status').textContent='Đang tải audit log…';$('audit-prev').disabled=true;$('audit-next').disabled=true;
  try{
    const data=await fetchAudit(query.toString());if(epoch!==generation||request!==sequence)return;
    next=data.nextCursor;$('audit-rows').replaceChildren();
    for(const entry of data.items){
      const row=node('tr');row.append(node('td',new Date(entry.createdAt).toLocaleString('vi-VN')));
      const action=node('td',labels[entry.action]||entry.action);action.append(node('small',entry.action));row.append(action);
      row.append(node('td',entry.actorId||'Ẩn danh'));
      const resource=node('td',entry.resourceType);if(entry.resourceId)resource.append(node('small',entry.resourceId));row.append(resource);
      const result=node('td');const badge=node('span',outcomeLabels[entry.outcome]||entry.outcome);badge.className='pill';badge.dataset.state=entry.outcome==='SUCCESS'?'ok':entry.outcome==='DENIED'?'stale':'error';result.append(badge,node('small','HTTP '+entry.httpStatus+' · '+entry.durationMs+' ms'));row.append(result,node('td',entry.requestId||'—'));$('audit-rows').append(row);
    }
    if(!data.items.length){const row=node('tr'),cell=node('td','Không có sự kiện khớp bộ lọc.');cell.colSpan=6;row.append(cell);$('audit-rows').append(row);}
    for(const [id,key] of [['audit-total','total'],['audit-success','success'],['audit-denied','denied'],['audit-failure','failure']])$(id).textContent=new Intl.NumberFormat('vi-VN').format(data.summary[key]);
    $('audit-page').textContent='Trang '+(history.length+1);$('audit-status').textContent='Cập nhật '+new Date().toLocaleTimeString('vi-VN')+' · Số liệu tổng theo bộ lọc. Chỉ trang đầu tự cập nhật.';
  }catch(error){if(epoch===generation&&request===sequence){next=null;$('audit-rows').replaceChildren();for(const id of ['audit-total','audit-success','audit-denied','audit-failure'])$(id).textContent='—';$('audit-status').textContent='Không tải được audit log. Kiểm tra kết nối và bộ lọc, rồi thử lại.';}}
  finally{if(epoch===generation&&request===sequence){$('audit-prev').disabled=!history.length;$('audit-next').disabled=next===null;}}
}
