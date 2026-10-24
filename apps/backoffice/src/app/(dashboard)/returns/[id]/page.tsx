import ReturnDetail from '@/views/returns/ReturnDetail'

export default function Page({params}:{params:{id:string}}) {return <ReturnDetail id={params.id}/>}
