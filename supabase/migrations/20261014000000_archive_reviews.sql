alter table public.reviews add column archived_at timestamptz;

create or replace function public.set_review_archived(review_id uuid, archived boolean)
returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.reviews r
  set archived_at = case when archived then coalesce(r.archived_at, now()) else null end
  where r.id = review_id and r.user_id = (select auth.uid());
  if not found then
    raise exception 'Review not found.';
  end if;
end
$$;

revoke execute on function public.set_review_archived(uuid, boolean) from public, anon;
grant execute on function public.set_review_archived(uuid, boolean) to authenticated;
