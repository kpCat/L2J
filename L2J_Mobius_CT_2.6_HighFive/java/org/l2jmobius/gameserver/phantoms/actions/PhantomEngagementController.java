/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.actions;

import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TargetDirective;

/** Session-owned continuation, advanced only by the existing decision writer under its native lease. */
public final class PhantomEngagementController
{
	public enum Outcome { STOCK, NATIVE, WAIT_NAV, APPROACH, TRANSIENT, UNAVAILABLE, EXHAUSTED, REVOKED }
	private record Token(long profileId,int objectId,long epoch,long goalId,long revision,long sequence,PlayerNativeEvidence.Target target,PhantomNavigationPoint targetPosition,int skillId,int range) { }
	private final PhantomNavigationService _navigation;
	private final java.util.function.BooleanSupplier _current;
	private final PhantomEngagementPlanner _planner=new PhantomEngagementPlanner();
	private final Set<PlayerNativeEvidence.Target> _excluded=new java.util.LinkedHashSet<>();
	private volatile TargetDirective.Mode _mode=TargetDirective.Mode.STOCK;
	private volatile Token _token;
	private volatile Outcome _outcome=Outcome.STOCK;
	private volatile boolean _revoked;
	private volatile int _npcId;
	private long _sequence,_started,_retryAt,_requestId,_requestDeadline,_nativeSince,_progressSequence;
	private int _positionIndex,_waypointIndex,_trackedIndex=-1;
	private List<PhantomNavigationPoint> _positions=List.of();
	private PhantomNavigationRoute _route;
	private PhantomNavigationPoint _lastNativePosition,_issued;
	private String _reason="action.stock";
	public PhantomEngagementController(PhantomNavigationService navigation,java.util.function.BooleanSupplier current){_navigation=navigation;_current=current;}
	public synchronized TargetDirective directive(Player player)
	{
		final Token token=_token; final var mode=_mode;
		if(_revoked||!_current.getAsBoolean()||mode==TargetDirective.Mode.HOLD){return new TargetDirective(TargetDirective.Mode.HOLD,null);}
		if(mode==TargetDirective.Mode.STOCK){return new TargetDirective(mode,null);}
		final var selected=player.getTarget();
		if(token!=null&&selected instanceof Creature corpse&&corpse.isAlikeDead()&&token.target().equals(PhantomEngagementPlanner.key(corpse))){return new TargetDirective(TargetDirective.Mode.STOCK,null);}
		final var target=restore(player,token);
		if(target==null||!_planner.eligible(player,target,_npcId)){return new TargetDirective(TargetDirective.Mode.HOLD,null);}
		final var opportunity=_planner.inspect(player,target,_npcId);
		return opportunity.kind()==PhantomEngagementPlanner.Kind.READY||opportunity.nativeDirect()?new TargetDirective(mode,target):new TargetDirective(TargetDirective.Mode.HOLD,null);
	}
	public synchronized Outcome advance(long profileId,Player player,PhantomGoal goal,int npcId,ToIntFunction<Creature> load,long now)
	{
		if(_revoked||!_current.getAsBoolean()||!owner(player)){clearRoute();_token=null;_positions=List.of();return publish(Outcome.REVOKED,TargetDirective.Mode.HOLD,"action.owner_revoked");}
		try
		{
		_npcId=npcId;
		if(player.isInParty()){clearRoute();return publish(Outcome.REVOKED,TargetDirective.Mode.HOLD,"action.party_takeover");}
		if(player.isCastingNow()||player.isCastingSimultaneouslyNow()||player.isAttackingNow()||player.getQueuedSkill()!=null){return _outcome;}
		final var selected=player.getTarget();
		// Corpse processing belongs to stock Sweep; attack validity is never a universal predicate.
		if(selected instanceof Creature corpse&&corpse.isAlikeDead()){clearRoute();_token=null;return publish(Outcome.STOCK,TargetDirective.Mode.STOCK,"action.stock_corpse");}
		if(player.isSitting()||player.isTeleporting()||player.isMovementDisabled()||player.isAllSkillsDisabled()){return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.native_busy");}
		final var evidence=player.getNativeWorkOwner().evidence();final var progress=evidence==null?null:evidence.snapshot();
		if(progress!=null&&progress.damageSequence()>_progressSequence){_progressSequence=progress.damageSequence();_excluded.clear();_started=now;}
		Token token=_token; final var prior=restore(player,token);
		if(token!=null&&(!matches(token,profileId,player,goal)||prior==null||!_planner.eligible(player,prior,npcId))){stopOwnedMove(player);clearRoute();_token=null;token=null;}
		var opportunity=prior!=null&&token!=null?_planner.inspect(player,prior,npcId):_planner.inspect(player,npcId,_excluded,load);
		if(opportunity.kind()==PhantomEngagementPlanner.Kind.UNAVAILABLE)
		{
			stopOwnedMove(player);clearRoute();_token=null;
			return publish(_excluded.size()>=3?Outcome.EXHAUSTED:Outcome.UNAVAILABLE,TargetDirective.Mode.STOCK,"action.no_local_opportunity");
		}
		if(token==null||!token.target().equals(opportunity.target()))
		{
			clearRoute();if(_started==0){_started=now;}
			_token=token=new Token(profileId,player.getObjectId(),player.getNativeWorkOwner().epoch(),goal.goalId(),goal.revision(),++_sequence,opportunity.target(),opportunity.targetPosition(),opportunity.skillId(),opportunity.range());
			_positions=opportunity.positions();_positionIndex=0;_nativeSince=now;_lastNativePosition=PhantomNativeTravelActions.point(player);
		}
		if(now-_started>=_navigation.policy().maximumAttemptDurationNanos()){stopOwnedMove(player);clearRoute();return publish(Outcome.EXHAUSTED,TargetDirective.Mode.HOLD,"action.attempt_deadline");}
		final var target=restore(player,token);
		if(target==null){clearRoute();return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.target_changed");}
		if(!token.targetPosition().equals(opportunity.targetPosition())||token.skillId()!=opportunity.skillId()||token.range()!=opportunity.range())
		{
			// A route to the old capability's stand-point cannot authorize the replacement action.
			stopOwnedMove(player);clearRoute();_positions=opportunity.positions();_positionIndex=0;
			_token=token=new Token(profileId,player.getObjectId(),token.epoch(),goal.goalId(),goal.revision(),++_sequence,opportunity.target(),opportunity.targetPosition(),opportunity.skillId(),opportunity.range());
		}
		if(opportunity.kind()==PhantomEngagementPlanner.Kind.READY)
		{
			stopOwnedMove(player);clearRoute();return publish(Outcome.NATIVE,TargetDirective.Mode.USE_EXACT_TARGET,"action.ready_native");
		}
		final var current=PhantomNativeTravelActions.point(player);
		if(_requestId==0&&opportunity.nativeDirect())
		{
			if(_lastNativePosition==null||current.distanceTo(_lastNativePosition)>=_navigation.policy().minimumProgress()){_lastNativePosition=current;_nativeSince=now;}
			if(now-_nativeSince<_navigation.policy().stuckWindowNanos()){return publish(Outcome.NATIVE,TargetDirective.Mode.USE_EXACT_TARGET,"action.stock_range_approach");}
		}
		publish(Outcome.WAIT_NAV,TargetDirective.Mode.HOLD,"action.plan_approach");
		if(now<_retryAt){return _outcome;}
		if(_requestId==0)
		{
			if(_positionIndex>=Math.min(3,_positions.size())){return failAttempt(player,token,"action.positions_exhausted");}
			stopOwnedMove(player);
			if(player.hasAI()&&player.getAI().getIntention()!=Intention.IDLE){player.getAI().setIntention(Intention.IDLE);}
			_requestDeadline=now+_navigation.policy().defaultRequestDeadlineNanos();
			final var submission=_navigation.submitWalkCorridor(new PhantomNavigationRequest(profileId,current,_positions.get(_positionIndex),now,_requestDeadline,_navigation.policy().maximumRouteDistance()));
			_requestId=submission.requestId();
			if(submission.immediateResult()!=null)
			{
				_navigation.consume(_requestId);if(!accept(player,token,submission.immediateResult(),now)){return _outcome;}
			}
			else if(submission.status()!=PhantomNavigationService.SubmissionStatus.ACCEPTED){clearRoute();_retryAt=now+_navigation.policy().pathfindingCooldownNanos();return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.submission_rejected");}
		}
		if(_route==null)
		{
			final var result=_navigation.consume(_requestId).orElse(null);
			if(result==null)
			{
				if(now>=_requestDeadline){clearRoute();_positionIndex++;_retryAt=now+_navigation.policy().pathfindingCooldownNanos();}
				return publish(Outcome.WAIT_NAV,TargetDirective.Mode.HOLD,"action.navigation_pending");
			}
			if(!accept(player,token,result,now)){return _outcome;}
		}
		if(!matches(token,profileId,player,goal)||restore(player,token)==null){clearRoute();return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.stale_reply");}
		// The navigation arrival radius advances intermediate legs. The final action point is complete
		// only after the independent native capability/LOS check above says READY.
		while(_waypointIndex<_route.waypoints().size()-1&&current.distanceTo(_route.waypoints().get(_waypointIndex))<=_navigation.arrivalRadius()
			&&new L2jNavigationBackend().canWalkDirect(current,_route.waypoints().get(_waypointIndex+1))){_waypointIndex++;}
		if(_waypointIndex>=_route.waypoints().size()){stopOwnedMove(player);clearRoute();_positionIndex++;return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.position_reinspect");}
		final var waypoint=_route.waypoints().get(_waypointIndex);
		if(_trackedIndex!=_waypointIndex)
		{
			_navigation.progressTracker().cancel(profileId,_requestId);
			final var leg=new PhantomNavigationRoute(_route.mode(),current,waypoint,List.of(waypoint),_route.geodataCapability(),now,false,1,_navigation.policy().maximumRouteDistance());
			if(_navigation.progressTracker().begin(profileId,_requestId,leg,now).status()!=PhantomNavigationProgressTracker.BeginStatus.TRACKING){return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.progress_busy");}
			_trackedIndex=_waypointIndex;
		}
		// Near a turn, the next edge may still be blocked. Preserve that native leg until
		// the fresh edge above permits advancing; radius proximity cannot erase a corner.
		final boolean nearWaypoint=current.distanceTo(waypoint)<=_navigation.arrivalRadius();
		final PhantomNavigationProgressTracker.ProgressStatus status;
		if(nearWaypoint)
		{
			if(_lastNativePosition==null||current.distanceTo(_lastNativePosition)>=_navigation.policy().minimumProgress()){_lastNativePosition=current;_nativeSince=now;}
			status=now-_nativeSince>=_navigation.policy().stuckWindowNanos()?PhantomNavigationProgressTracker.ProgressStatus.STUCK:PhantomNavigationProgressTracker.ProgressStatus.TRACKING;
		}
		else { status=_navigation.progressTracker().observe(profileId,_requestId,current,now).status();_lastNativePosition=current;_nativeSince=now; }
		if(status==PhantomNavigationProgressTracker.ProgressStatus.STUCK||status==PhantomNavigationProgressTracker.ProgressStatus.TIMEOUT||status==PhantomNavigationProgressTracker.ProgressStatus.STALE){stopOwnedMove(player);clearRoute();_positionIndex++;_retryAt=now+_navigation.policy().pathfindingCooldownNanos();return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.progress_"+status);}
		if(!player.isMoving())
		{
			if(!new L2jNavigationBackend().canWalkDirect(current,waypoint)){clearRoute();_positionIndex++;return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.edge_changed");}
			if(!matches(token,profileId,player,goal)||restore(player,token)==null){clearRoute();return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.stale_command");}
			player.getAI().setIntention(Intention.MOVE_TO,new Location(waypoint.x(),waypoint.y(),waypoint.z()));
			_issued=new PhantomNavigationPoint(player.getXdestination(),player.getYdestination(),player.getZdestination(),player.getInstanceId());
			if(player.isMoving()&&!new L2jNavigationBackend().canWalkDirect(current,_issued)){stopOwnedMove(player);clearRoute();_positionIndex++;return publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.native_destination_changed");}
		}
		return publish(Outcome.APPROACH,TargetDirective.Mode.HOLD,"action.native_walking");
		}
		finally { if(_revoked||!_current.getAsBoolean()){stopOwnedMove(player);clearRoute();} }
	}
	private boolean accept(Player player,Token token,PhantomNavigationResult result,long now)
	{
		if(_revoked||!_current.getAsBoolean()||token!=_token||result.profileId()!=token.profileId()||result.requestId()!=_requestId||restore(player,token)==null){clearRoute();publish(Outcome.TRANSIENT,TargetDirective.Mode.HOLD,"action.stale_reply");return false;}
		if(result.route()!=null&&result.status()!=PhantomNavigationResult.Status.DIRECT_UNVERIFIED_NO_GEODATA){_route=result.route();_waypointIndex=0;_trackedIndex=-1;return true;}
		final boolean transientResult=switch(result.status()){case PROFILE_BUSY,QUEUE_BACKPRESSURE,COOLDOWN,SERVICE_NOT_RUNNING,PATHFINDING_DISABLED,DEADLINE_EXPIRED -> true;default -> false;};
		clearRoute();_retryAt=now+_navigation.policy().pathfindingCooldownNanos();if(!transientResult){_positionIndex++;}
		publish(transientResult?Outcome.TRANSIENT:Outcome.UNAVAILABLE,TargetDirective.Mode.HOLD,"action.navigation_"+result.status());return false;
	}
	private Outcome failAttempt(Player player,Token token,String reason)
	{
		stopOwnedMove(player);clearRoute();if(_excluded.size()<8){_excluded.add(token.target());}
		if(player.getTarget() instanceof Creature selected&&token.target().equals(PhantomEngagementPlanner.key(selected))){player.setTarget(null);}
		_token=null;return publish(_excluded.size()>=3?Outcome.EXHAUSTED:Outcome.UNAVAILABLE,TargetDirective.Mode.HOLD,reason);
	}
	private void stopOwnedMove(Player player)
	{
		if(_issued!=null&&player.isMoving()&&player.getAI().getIntention()==Intention.MOVE_TO&&player.getXdestination()==_issued.x()&&player.getYdestination()==_issued.y()&&player.getZdestination()==_issued.z()){player.stopMove(null);}
		_issued=null;
	}
	private void clearRoute()
	{
		final Token token=_token;
		if(_requestId>0&&token!=null){_navigation.cancel(token.profileId(),_requestId);_navigation.consume(_requestId);_navigation.progressTracker().cancel(token.profileId(),_requestId);}
		_requestId=0;_route=null;_trackedIndex=-1;
	}
	public synchronized void revoke(){_revoked=true;_mode=TargetDirective.Mode.HOLD;clearRoute();_token=null;_positions=List.of();_excluded.clear();}
	/** Caller holds the session writer and its exact native action lease. */
	public synchronized void stopMovement(Player player,long epoch)
	{
		if(owner(player)&&player.getNativeWorkOwner().epoch()==epoch){stopOwnedMove(player);}
	}
	public boolean holdsMovement(){return _mode==TargetDirective.Mode.HOLD;}
	public synchronized java.util.Map<String,String> snapshot(){return java.util.Map.of("actionOutcome",_outcome.name(),"actionReason",_reason,"actionMode",_mode.name(),"actionRequestId",Long.toString(_requestId),"actionSequence",Long.toString(_sequence),"actionTarget",String.valueOf(_token==null?null:_token.target()),"actionStartedNanos",Long.toString(_started));}
	private Outcome publish(Outcome outcome,TargetDirective.Mode mode,String reason){_reason=reason;_outcome=outcome;_mode=mode;return outcome;}
	private static boolean owner(Player player){final var owner=player.getNativeWorkOwner();return owner!=null&&owner.isCurrent()&&!owner.sealed()&&owner.player()==player&&player.isOnline()&&!player.isAlikeDead()&&!player.hasPendingOwnedStore()&&World.getInstance().getPlayer(player.getObjectId())==player;}
	private boolean matches(Token token,long profileId,Player player,PhantomGoal goal){return !_revoked&&_current.getAsBoolean()&&token.profileId()==profileId&&token.objectId()==player.getObjectId()&&owner(player)&&token.epoch()==player.getNativeWorkOwner().epoch()&&token.goalId()==goal.goalId()&&token.revision()==goal.revision();}
	private static Creature restore(Player player,Token token)
	{
		if(token==null||!owner(player)||token.objectId()!=player.getObjectId()||token.epoch()!=player.getNativeWorkOwner().epoch()){return null;}
		final var object=World.getInstance().findObject(token.target().objectId());
		return object instanceof Creature target&&!target.isAlikeDead()&&target.getInstanceId()==player.getInstanceId()&&token.target().equals(PhantomEngagementPlanner.key(target))?target:null;
	}
}
