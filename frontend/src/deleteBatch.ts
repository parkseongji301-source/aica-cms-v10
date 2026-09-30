export type DeleteTarget = {id:number;label:string;revision?:number};
export type BatchFailure = {id:number;label:string;message:string};
export async function deleteSequentially(targets:DeleteTarget[],remove:(target:DeleteTarget)=>Promise<unknown>,progress:(done:number)=>void=()=>{}) {
  const succeeded:number[]=[],failed:BatchFailure[]=[];
  let stopped=false;
  for(const target of targets){
    if(stopped){failed.push({...target,message:'연결 또는 권한 문제로 처리하지 않았습니다. 목록을 새로 확인하세요.'});continue;}
    try{await remove(target);succeeded.push(target.id);}
    catch(error){
      failed.push({...target,message:error instanceof Error?error.message:'삭제하지 못했습니다.'});
      const status=(error as {status?:number}|null)?.status;
      stopped=error instanceof TypeError||status===401||status===403;
    }
    progress(succeeded.length+failed.length);
  }
  return {succeeded,failed};
}
