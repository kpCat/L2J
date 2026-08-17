# Goal 026 CP2 — exact High Five source context

Baseline: `e3f44333df659d3ba3f258739e1e0bba8bb6a53b`.

## Canonical MPCC packet path

`ExClientPackets` registers:
- `RequestExAskJoinMPCC`
- `RequestExAcceptJoinMPCC`
- `RequestExOustFromMPCC`

### Invite
`RequestExAskJoinMPCC` currently owns the lifecycle inline:
- packet resolves client target name through `World.getInstance().getPlayer(name)`;
- requester must be Party leader;
- if already in CommandChannel, requester must be CC leader;
- target must be in another Party and target Party must not already be in CC;
- formation right is exactly one of:
  1. clan leader with clan level >= 5;
  2. item 8871 Strategy Guide;
  3. pledge class >= 5 and known skill 391 Clan Imperium;
- actual invitee is target Party's current leader;
- if target leader is not processing a request,
  `requester.onTransactionRequest(targetLeader)` establishes the ordinary
  Player request relation/timeout;
- target leader receives normal MPCC invitation packets.

World/name lookup is transport-only. The reusable service must take exact Player
references and must not discover targets globally.

### Accept/refuse
`RequestExAcceptJoinMPCC` currently:
- reads `player.getActiveRequester()`;
- ACCEPT: creates `new CommandChannel(requestor)` if needed, then
  `requestor.getParty().getCommandChannel().addParty(player.getParty())`;
- REFUSE: informs requester;
- terminal response clears `player.setActiveRequester(null)` and
  `requestor.onTransactionResponse()`.

There is no shared transport-neutral MPCC service on this baseline.

### Dismiss
`RequestExOustFromMPCC` currently validates same-channel authority and directly
calls `removeParty(target.getParty())`.

## Canonical mutation owner

`CommandChannel` already owns constructor/add/remove/disband and the ordinary
MPCC broadcasts/UI state. Do not duplicate that state or call
`Party#setCommandChannel` from Phantom code.

`Party.removePartyMember(...)` already preserves teardown: collapsing a member
Party is removed from CC; collapsing CC leader Party disbands the channel.

## Existing precedent

Goal017 already uses generic `PartyInvitationService`, a canonical
transport-neutral Party invitation/membership facade. `PhantomPartyBackend` /
`L2jPhantomPartyBackend` delegate ordinary Party mutations through it.

Player request authority already supplies timeout/exact requester:
`onTransactionRequest`, `isProcessingRequest`, `onTransactionResponse`.
No new timer is needed.
