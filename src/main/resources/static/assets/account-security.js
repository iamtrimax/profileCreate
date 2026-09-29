import {api,$,node,action,form,notice,refreshCsrf} from './common.js';

export function initAccountSecurity(){
  const forgot=node('form');forgot.id='forgot-form';forgot.hidden=true;
  forgot.innerHTML='<h2>Quên mật khẩu</h2><label>Email tài khoản<input name="email" type="email" autocomplete="email" maxlength="254" required></label><button type="submit">Gửi liên kết đặt lại</button>';
  $('login-form').after(forgot);
  $('login-form').append(action('Quên mật khẩu?',()=>{forgot.hidden=!forgot.hidden;}));
  form('forgot-form',async data=>{await api('/auth/password-reset/request','POST',data);notice('Nếu email đã đăng ký, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu.');});
  const params=new URLSearchParams(location.hash.slice(1));
  const purpose=params.has('reset')?'reset':params.has('verify')?'verify':null;
  if(!purpose)return;
  const token=params.get(purpose);history.replaceState(null,'',location.pathname+location.search);
  const panel=node('section',undefined,'card');panel.id='token-panel';
  panel.append(node('h2',purpose==='reset'?'Đặt lại mật khẩu':'Xác minh email'));
  const confirm=node('form');confirm.id='token-form';
  if(purpose==='reset')confirm.innerHTML='<label>Mật khẩu mới<input name="password" type="password" required minlength="10" maxlength="64" autocomplete="new-password"></label><label>Nhập lại mật khẩu<input name="confirmation" type="password" required minlength="10" maxlength="64" autocomplete="new-password"></label>';
  const button=node('button',purpose==='reset'?'Lưu mật khẩu mới':'Xác nhận email');button.type='submit';confirm.append(button);panel.append(confirm);$('auth').before(panel);
  form('token-form',async data=>{
    if(purpose==='reset'&&data.password!==data.confirmation)throw new Error('Hai mật khẩu chưa khớp.');
    await api(purpose==='reset'?'/auth/password-reset/confirm':'/auth/email-verification/confirm','POST',purpose==='reset'?{token,password:data.password}:{token});
    panel.replaceChildren(node('h2',purpose==='reset'?'Đã đổi mật khẩu. Hãy đăng nhập lại.':'Email đã được xác minh.'));
    panel.append(action('Tiếp tục',()=>location.assign('/app')));await refreshCsrf();
    if(purpose==='reset'){window.dispatchEvent(new Event('account-session-reset'));$('dashboard').hidden=true;$('auth').hidden=false;}
    else if($('account'))renderAccount(await api('/me').catch(()=>null));
    notice(purpose==='reset'?'Mật khẩu đã đổi, các phiên cũ đã hết hiệu lực.':'Xác minh email thành công.');
  });
}
export function renderAccount(me){
  if(!me)return;
  let panel=$('account');if(!panel){panel=node('section',undefined,'card account-security');panel.id='account';$('profile-editor').before(panel);}
  const info=node('div',undefined,'account-security-copy');
  info.append(node('h2','Bảo mật tài khoản'),node('p',me.email),node('p',me.emailVerified?'✓ Email đã xác minh':'Email chưa được xác minh','muted'));
  panel.replaceChildren(info);
  if(!me.emailVerified)panel.append(action('Gửi email xác minh',async()=>{await api('/me/email-verification','POST');notice('Đã yêu cầu gửi email xác minh. Liên kết có hiệu lực 24 giờ.');}));
}
export async function loadEngagement(){
  const result=await api('/me/analytics/engagement');let panel=$('engagement');
  if(!panel){panel=node('div');panel.id='engagement';$('daily').before(panel);$('analytics-panel').querySelector('p.muted').textContent='Lượt xem theo giờ Việt Nam, đã lọc bot phổ biến. Dữ liệu cũ vẫn được giữ trong tổng lượt xem.';}
  panel.replaceChildren();
  const stats=node('div',undefined,'engagement-stats');
  for(const [label,value] of [['Lượt xem mới',result.views],['Tổng khách duy nhất từng ngày',result.dailyUniqueVisitors],['Nhấp liên kết',result.clicks]]){const card=node('div',undefined,'list-item');card.append(node('strong',String(value)),node('small',label));stats.append(card);}
  panel.append(stats,node('p','Trong 30 ngày. Một trình duyệt được tính một lần mỗi ngày; không phải số người duy nhất trong cả tháng.','muted'));
  const groups=node('div',undefined,'engagement-groups');
  for(const [title,items] of [['Nguồn truy cập',result.referrers],['Liên kết được nhấp',result.links]]){const group=node('div');group.append(node('h3',title));if(!items.length)group.append(node('p','Chưa có dữ liệu.','muted'));for(const item of items){const row=node('div',undefined,'day-row');row.append(node('span',item.label),node('strong',String(item.count)));group.append(row);}groups.append(group);}
  panel.append(groups);
}
