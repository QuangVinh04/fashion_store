import {api,ApiError} from './client'

export async function uploadEvidence(file:File,purpose:'returns'|'payments') {
  if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size<=0||file.size>5242880)throw new Error('Ảnh cần là JPEG, PNG hoặc WebP, tối đa 5 MiB.')
  const signed=await api.post<{mediaId:string;uploadUrl:string;uploadHeaders:Record<string,string>}>(`/api/v1/files/evidence/${purpose}/presign`,{filename:file.name,contentType:file.type,sizeBytes:file.size,visibility:'PRIVATE'})

  if(!['http:','https:'].includes(new URL(signed.uploadUrl).protocol))throw new Error('Đường dẫn tải ảnh chưa hợp lệ.')
  const put=await fetch(signed.uploadUrl,{method:'PUT',body:file,headers:signed.uploadHeaders,credentials:'omit'})

  if(!put.ok)throw new ApiError(put.status,put.status,'Không tải được ảnh bằng chứng. Vui lòng thử lại.')
  const completed=await api.post<{id:string;purpose:string;status:string;visibility:string}>(`/api/v1/files/${encodeURIComponent(signed.mediaId)}/complete`,{})

  if(completed.id!==signed.mediaId || completed.purpose!==(purpose==='returns'?'RETURN_EVIDENCE':'PAYMENT_EVIDENCE') || completed.status!=='ACTIVE'||completed.visibility!=='PRIVATE')throw new Error('Ảnh bằng chứng chưa được xác minh.')
  
return completed.id
}
