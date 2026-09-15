package com.itsazni.notificationforwarder.worker
import com.itsazni.notificationforwarder.data.QueueItem
internal data class DeliveryOutcome(val success:Boolean,val permanentFailure:Boolean=false,val message:String="",val httpStatus:Int?=null)
internal suspend fun processWithAtomicClaim(item:QueueItem,claim:suspend(Long)->Boolean,deliver:suspend(QueueItem)->DeliveryOutcome,onSuccess:suspend(QueueItem)->Unit,onFailure:suspend(QueueItem,DeliveryOutcome)->Unit):QueueProcessResult{
    if(!claim(item.id))return QueueProcessResult(false,false)
    val outcome=deliver(item)
    if(outcome.success){onSuccess(item);return QueueProcessResult(true,false)}
    onFailure(item,outcome);return QueueProcessResult(true,!outcome.permanentFailure)
}
