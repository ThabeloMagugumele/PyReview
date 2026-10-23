revoke execute on function public.rls_auto_enable() from public, anon, authenticated;

create index coding_standards_department_idx on public.coding_standards (department_id);
create index coding_standards_uploaded_by_idx on public.coding_standards (uploaded_by);

comment on table public.department_invites is
  'Invite codes per department. RLS on with no policies on purpose: clients can only reach codes through join_department() and my_department_invite_code().';
