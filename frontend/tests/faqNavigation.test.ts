import test from 'node:test';
import assert from 'node:assert/strict';
import {contentContext,contentSections,contextualPostPath,draftSelection,returnSectionPath,scopedPostParams,sectionPath} from '../src/contentNavigation.ts';
import {classificationProblem,invalidTopicIds} from '../src/classification.ts';
import {contentPresentation} from '../src/contentPresentation.ts';
import {postFingerprint} from '../src/contentDocument.ts';
import type {ClassificationCatalog,PostDocument} from '../src/types';
const catalog:ClassificationCatalog={types:[{code:'REVIEW',name:'후기',active:true,sortOrder:1},{code:'FAQ',name:'FAQ',active:true,sortOrder:2}],cohorts:[],
  topics:[{id:91,code:'REVIEW_LIFE',name:'생활',active:true},...contentSections.FAQ.nodes.filter(n=>n.code).map((n,i)=>({id:301+i,code:n.code!,name:n.label,active:true}))],
  allowedTopics:[{typeCode:'REVIEW',topicId:91},...Array.from({length:7},(_,i)=>({typeCode:'FAQ',topicId:301+i}))]};
test('all FAQ locations use their real IDs and never resolve the same-named review topic',()=>{
  assert.equal(contentSections.FAQ.parent,'지원 전 Check!!');
  for(const node of contentSections.FAQ.nodes){
    const query=new URLSearchParams(sectionPath('FAQ',node.key,catalog)!.split('?')[1]);
    assert.equal(query.get('typeCodes'),'FAQ');
    assert.equal(query.get('topicIds'),node.code?String(catalog.topics.find(t=>t.code===node.code)!.id):null);
  }
  assert.notEqual(sectionPath('FAQ','life',catalog),sectionPath('REVIEW','life',catalog));
});
test('FAQ context uses its location and visible search/status without hidden advanced filters',()=>{
  const input=new URLSearchParams('faqSection=life&typeCodes=REVIEW&topicIds=91&cohortIds=6,7&categoryId=12&status=DRAFT&q=park');
  const scope=contentContext(input,catalog)!;const result=scopedPostParams(input,scope);
  assert.equal(result.get('typeCodes'),'FAQ');assert.equal(result.get('topicIds'),'304');
  assert.equal(result.has('cohortIds'),false);assert.equal(result.has('categoryId'),false);
  assert.equal(result.get('q'),'park');assert.equal(result.get('status'),'DRAFT');assert.equal(result.has('faqSection'),false);
  const all=scopedPostParams(input,contentContext(new URLSearchParams('faqSection=all'),catalog));
  assert.equal(all.has('topicIds'),false);
  const unscoped=scopedPostParams(input,null);
  assert.equal(unscoped.get('cohortIds'),'6,7');assert.equal(unscoped.get('categoryId'),'12');assert.equal(unscoped.get('topicIds'),'91');
  assert.ok(contentContext(new URLSearchParams('reviewSection=life&faqSection=life'),catalog)!.error);
  assert.equal(sectionPath('FAQ','life',{...catalog,allowedTopics:[]}),null);
});
test('new FAQ suggests only the selected topic, no cohort, and shares editor ID and list context',()=>{
  const scope=contentContext(new URLSearchParams('faqSection=preparation'),catalog)!;
  assert.deepEqual(draftSelection(scope),{typeCode:'FAQ',cohortIds:[],topicIds:[301]});
  assert.equal(contextualPostPath(73,scope).split('?')[0],contextualPostPath(73,null));
  assert.equal(returnSectionPath(new URLSearchParams('faqSection=preparation'),catalog),sectionPath('FAQ','preparation',catalog));
  assert.deepEqual(draftSelection(contentContext(new URLSearchParams('faqSection=all'),catalog)!).topicIds,[]);
});
test('changing FAQ to REVIEW keeps disallowed topic until explicit correction and remains dirty',()=>{
  const baseline={typeCode:'FAQ',cohortIds:[],topicIds:[304]},changed={...baseline,typeCode:'REVIEW'};
  assert.deepEqual(changed.topicIds,[304]);assert.deepEqual(invalidTopicIds(changed,catalog),[304]);
  assert.ok(classificationProblem(changed,catalog,baseline));
  assert.equal(classificationProblem({...changed,topicIds:[91]},catalog,baseline),'');
  const doc={title:'질문',content:'답변',richContent:null,categoryId:null,classification:baseline} as unknown as PostDocument;
  assert.notEqual(postFingerprint(doc),postFingerprint({...doc,classification:{...doc.classification,...changed}}));
  assert.notEqual(postFingerprint(doc),postFingerprint({...doc,content:'답변 수정'}));
});
test('FAQ changes display vocabulary while the stored fields stay title/content',()=>{
  const faq=contentPresentation('FAQ'),general=contentPresentation('GENERAL');
  assert.equal(faq.title,'질문');assert.equal(faq.body,'답변');assert.equal(faq.editorLabel,'FAQ 답변');
  assert.equal(general.title,'제목');assert.equal(general.body,'본문');
  assert.deepEqual(contentPresentation('REVIEW'),general);
});
