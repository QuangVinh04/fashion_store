import WalletDetail from '@/views/wallets/WalletDetail'

export default function Page({params,searchParams}:{params:{userId:string};searchParams:{ledgerId?:string}}) {return <WalletDetail userId={params.userId} ledgerId={searchParams.ledgerId}/>}
