create schema if not exists private;

create table public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null unique check (length(trim(name)) between 1 and 120),
  created_at timestamptz not null default now()
);

create table public.departments (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  name text not null check (length(trim(name)) between 1 and 120),
  created_at timestamptz not null default now(),
  unique (organization_id, name)
);
create index departments_organization_idx on public.departments (organization_id);

create table public.department_invites (
  department_id uuid primary key references public.departments (id) on delete cascade,
  code text not null unique check (code ~ '^[A-Z0-9]{6,12}$'),
  created_at timestamptz not null default now()
);

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  full_name text not null default '' check (length(full_name) <= 120),
  email text,
  auth_provider text not null default 'email',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.memberships (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  department_id uuid not null references public.departments (id) on delete cascade,
  role text not null default 'member' check (role in ('member', 'admin')),
  created_at timestamptz not null default now()
);
create index memberships_department_idx on public.memberships (department_id);

create table public.coding_standards (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations (id) on delete cascade,
  department_id uuid references public.departments (id) on delete cascade,
  title text not null check (length(trim(title)) between 1 and 200),
  description text not null default '' check (length(description) <= 2000),
  version text not null default '' check (length(version) <= 40),
  content text not null check (length(content) between 1 and 200000),
  file_path text,
  uploaded_by uuid references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index coding_standards_scope_idx on public.coding_standards (organization_id, department_id);

create table public.submissions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  department_id uuid references public.departments (id) on delete set null,
  file_name text not null check (length(trim(file_name)) between 1 and 120),
  submission_method text not null check (submission_method in ('TEXT', 'FILE', 'IMAGES')),
  code text not null check (length(code) between 1 and 200000),
  reconstructed_code text check (reconstructed_code is null or length(reconstructed_code) <= 200000),
  image_count int not null default 0 check (image_count between 0 and 10),
  uncertain_line_count int not null default 0 check (uncertain_line_count >= 0),
  status text not null default 'PENDING' check (status in ('PENDING', 'REVIEWED', 'FAILED')),
  created_at timestamptz not null default now()
);
create index submissions_user_created_idx on public.submissions (user_id, created_at desc);
create index submissions_department_idx on public.submissions (department_id);

create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  submission_id uuid not null unique references public.submissions (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  status text not null check (status in ('COMPLETED', 'FAILED')),
  summary text not null default '',
  standards_used text not null default '',
  model text not null default '',
  good_count int not null default 0,
  warning_count int not null default 0,
  error_count int not null default 0,
  requested_at timestamptz not null,
  completed_at timestamptz not null default now()
);
create index reviews_user_requested_idx on public.reviews (user_id, requested_at desc);

create table public.review_findings (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.reviews (id) on delete cascade,
  position int not null,
  severity text not null check (severity in ('GOOD', 'WARNING', 'ERROR')),
  category text not null,
  title text not null,
  line_start int not null default 0,
  line_end int not null default 0,
  explanation text not null,
  learn_more_topic text not null default '',
  confidence text not null default 'HIGH' check (confidence in ('HIGH', 'MEDIUM', 'LOW')),
  code_reference text not null default '',
  unique (review_id, position)
);

create or replace function private.my_department_id()
returns uuid language sql stable security definer set search_path = '' as $$
  select department_id from public.memberships where user_id = (select auth.uid())
$$;

create or replace function private.my_organization_id()
returns uuid language sql stable security definer set search_path = '' as $$
  select d.organization_id
  from public.memberships m join public.departments d on d.id = m.department_id
  where m.user_id = (select auth.uid())
$$;

create or replace function private.is_department_admin()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.memberships where user_id = (select auth.uid()) and role = 'admin'
  )
$$;

create or replace function private.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.updated_at = now();
  return new;
end
$$;

create trigger profiles_touch before update on public.profiles
  for each row execute function private.touch_updated_at();
create trigger coding_standards_touch before update on public.coding_standards
  for each row execute function private.touch_updated_at();

create or replace function private.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  v_code text := upper(trim(coalesce(new.raw_user_meta_data ->> 'invite_code', '')));
  v_department uuid;
begin
  insert into public.profiles (id, full_name, email, auth_provider)
  values (
    new.id,
    left(coalesce(nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
                  split_part(coalesce(new.email, ''), '@', 1)), 120),
    new.email,
    coalesce(new.raw_app_meta_data ->> 'provider', 'email')
  );
  if v_code <> '' then
    select department_id into v_department from public.department_invites where code = v_code;
    if v_department is not null then
      insert into public.memberships (user_id, department_id, role) values (new.id, v_department, 'member');
    end if;
  end if;
  return new;
end
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function private.handle_new_user();

revoke all on all functions in schema private from public, anon;
grant usage on schema private to authenticated, service_role;
grant execute on function private.my_department_id(), private.my_organization_id(),
  private.is_department_admin() to authenticated, service_role;

create or replace function public.join_department(invite_code text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  v_department uuid;
begin
  if (select auth.uid()) is null then
    raise exception 'Sign in first.';
  end if;
  if exists (select 1 from public.memberships where user_id = (select auth.uid())) then
    raise exception 'You already belong to a department. Ask an administrator to move you.';
  end if;
  select department_id into v_department
  from public.department_invites where code = upper(trim(invite_code));
  if v_department is null then
    raise exception 'That invite code isn''t valid. Check it with your instructor.';
  end if;
  insert into public.memberships (user_id, department_id) values ((select auth.uid()), v_department);
  return v_department;
end
$$;

create or replace function public.my_department_invite_code()
returns text language sql stable security definer set search_path = '' as $$
  select i.code
  from public.department_invites i
  join public.memberships m on m.department_id = i.department_id
  where m.user_id = (select auth.uid()) and m.role = 'admin'
$$;

revoke execute on function public.join_department(text), public.my_department_invite_code() from public, anon;
grant execute on function public.join_department(text), public.my_department_invite_code() to authenticated;

grant usage on schema public to authenticated, service_role;
grant all on all tables in schema public to service_role;
grant select on public.organizations, public.departments, public.memberships to authenticated;
grant select, update (full_name) on public.profiles to authenticated;
grant select, insert, update, delete on public.coding_standards to authenticated;
grant select, insert, delete on public.submissions to authenticated;
grant select on public.reviews, public.review_findings to authenticated;

alter table public.organizations enable row level security;
alter table public.departments enable row level security;
alter table public.department_invites enable row level security;
alter table public.profiles enable row level security;
alter table public.memberships enable row level security;
alter table public.coding_standards enable row level security;
alter table public.submissions enable row level security;
alter table public.reviews enable row level security;
alter table public.review_findings enable row level security;

create policy "Members read their organisation" on public.organizations
  for select to authenticated using (id = (select private.my_organization_id()));

create policy "Members read their organisation's departments" on public.departments
  for select to authenticated using (organization_id = (select private.my_organization_id()));

create policy "Users read their own profile" on public.profiles
  for select to authenticated using (id = (select auth.uid()));
create policy "Users update their own profile" on public.profiles
  for update to authenticated using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy "Users read their own membership" on public.memberships
  for select to authenticated using (user_id = (select auth.uid()));

create policy "Members read applicable standards" on public.coding_standards
  for select to authenticated using (
    organization_id = (select private.my_organization_id())
    and (department_id is null or department_id = (select private.my_department_id()))
  );
create policy "Admins add standards in their scope" on public.coding_standards
  for insert to authenticated with check (
    (select private.is_department_admin())
    and organization_id = (select private.my_organization_id())
    and (department_id is null or department_id = (select private.my_department_id()))
    and uploaded_by = (select auth.uid())
  );
create policy "Admins change standards in their scope" on public.coding_standards
  for update to authenticated
  using (
    (select private.is_department_admin())
    and organization_id = (select private.my_organization_id())
    and (department_id is null or department_id = (select private.my_department_id()))
  )
  with check (
    (select private.is_department_admin())
    and organization_id = (select private.my_organization_id())
    and (department_id is null or department_id = (select private.my_department_id()))
  );
create policy "Admins delete standards in their scope" on public.coding_standards
  for delete to authenticated using (
    (select private.is_department_admin())
    and organization_id = (select private.my_organization_id())
    and (department_id is null or department_id = (select private.my_department_id()))
  );

create policy "Users read their own submissions" on public.submissions
  for select to authenticated using (user_id = (select auth.uid()));
create policy "Users create submissions for their own department" on public.submissions
  for insert to authenticated with check (
    user_id = (select auth.uid())
    and department_id is not distinct from (select private.my_department_id())
    and status = 'PENDING'
  );
create policy "Users delete their own submissions" on public.submissions
  for delete to authenticated using (user_id = (select auth.uid()));

create policy "Users read their own reviews" on public.reviews
  for select to authenticated using (user_id = (select auth.uid()));

create policy "Users read findings of their own reviews" on public.review_findings
  for select to authenticated using (
    exists (select 1 from public.reviews r where r.id = review_id and r.user_id = (select auth.uid()))
  );

insert into public.organizations (name) values ('Inspired Testing');

insert into public.departments (organization_id, name)
select o.id, d.name
from public.organizations o
cross join (values ('Automation Testing'), ('Manual Testing'), ('Software Development')) as d (name)
where o.name = 'Inspired Testing';

insert into public.department_invites (department_id, code)
select id, upper(substr(md5(gen_random_uuid()::text), 1, 8)) from public.departments;

insert into public.coding_standards (organization_id, department_id, title, description, version, content)
select id, null, 'Inspired Testing Python Style Guide', 'Organisation-wide basics for all Python code', '1.0',
  '- Function and variable names use snake_case; class names use PascalCase.' || chr(10) ||
  '- Every function has a docstring that says what it does.' || chr(10) ||
  '- Lines are at most 99 characters long.' || chr(10) ||
  '- No passwords, tokens or API keys in source code.' || chr(10) ||
  '- Catch specific exceptions; never use a bare except.'
from public.organizations where name = 'Inspired Testing';
