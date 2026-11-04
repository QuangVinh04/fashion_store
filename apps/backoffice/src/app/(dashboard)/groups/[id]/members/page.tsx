import GroupMembersView from '@/views/groups/GroupMembersView'

interface Props {
  params: {
    id: string
  }
}

export default function GroupMembersPage({ params }: Props) {
  return <GroupMembersView groupId={params.id} />
}
