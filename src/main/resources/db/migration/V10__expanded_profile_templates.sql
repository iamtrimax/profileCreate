alter table accounts drop constraint accounts_profile_template_check;
alter table accounts add constraint accounts_profile_template_check
    check (profile_template in (
        'CLASSIC', 'MINIMAL', 'STUDIO', 'MIDNIGHT', 'BUSINESS', 'PORTFOLIO', 'CREATOR',
        'EDITORIAL', 'BENTO', 'CONSULTANT', 'BOUTIQUE', 'BRUTALIST', 'TERMINAL', 'RESUME', 'SPOTLIGHT'
    ));
