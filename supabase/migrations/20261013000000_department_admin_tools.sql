create or replace function public.regenerate_department_invite_code()
returns text language plpgsql security definer set search_path = '' as $$
declare
  v_department uuid;
  v_code text;
begin
  select department_id into v_department
  from public.memberships where user_id = (select auth.uid()) and role = 'admin';
  if v_department is null then
    raise exception 'Only department admins can change the invite code.';
  end if;
  v_code := upper(substr(md5(gen_random_uuid()::text), 1, 8));
  update public.department_invites set code = v_code, created_at = now() where department_id = v_department;
  return v_code;
end
$$;

create or replace function public.department_members()
returns table (user_id uuid, full_name text, email text, role text, joined_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
declare
  v_department uuid;
begin
  select m.department_id into v_department
  from public.memberships m where m.user_id = (select auth.uid()) and m.role = 'admin';
  if v_department is null then
    raise exception 'Only department admins can see the member list.';
  end if;
  return query
    select m.user_id, p.full_name, p.email, m.role, m.created_at
    from public.memberships m join public.profiles p on p.id = m.user_id
    where m.department_id = v_department
    order by m.role, p.full_name;
end
$$;

create or replace function public.set_member_role(member_id uuid, new_role text)
returns void language plpgsql security definer set search_path = '' as $$
declare
  v_department uuid;
begin
  if new_role not in ('member', 'admin') then
    raise exception 'The role must be member or admin.';
  end if;
  if member_id = (select auth.uid()) then
    raise exception 'You can''t change your own role. Ask another admin.';
  end if;
  select department_id into v_department
  from public.memberships where user_id = (select auth.uid()) and role = 'admin';
  if v_department is null then
    raise exception 'Only department admins can change roles.';
  end if;
  update public.memberships set role = new_role
  where user_id = member_id and department_id = v_department;
  if not found then
    raise exception 'That person isn''t in your department.';
  end if;
end
$$;

revoke execute on function public.regenerate_department_invite_code(), public.department_members(),
  public.set_member_role(uuid, text) from public, anon;
grant execute on function public.regenerate_department_invite_code(), public.department_members(),
  public.set_member_role(uuid, text) to authenticated;
