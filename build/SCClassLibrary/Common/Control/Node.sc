
Node {
	
	var <>nodeID, <>server, <>group;
	var <>isPlaying = false, <>isRunning = false;
	classvar addActions;

	*initClass {
		addActions = (
			addToHead: 0, 
			addToTail: 1, 
			addBefore: 2, 
			addAfter: 3, 
			addReplace: 4,
			h: 0,
			t: 1,
				// valid action numbers should stay the same
			0: 0, 1: 1, 2: 2, 3: 3, 4: 4
		);
	}

	*basicNew { arg server, nodeID;
		server = server ? Server.default;
		^super.newCopyArgs(nodeID ?? { server.nextNodeID }, server)
	}

	*actionNumberFor { |addAction = (\addToHead)| ^addActions[addAction] }

	free { arg sendFlag=true;
		if(sendFlag, {
			server.sendMsg(11, nodeID);  //"/n_free"
		});
		group = nil;
		isPlaying = false;
		isRunning = false;
	}
	freeMsg { ^[11, nodeID] }

	run { arg flag=true;
		server.sendMsg(12, nodeID,flag.binaryValue); //"/n_run"
	}
	
	runMsg { arg flag=true;
		^[12, nodeID,flag.binaryValue]; //"/n_run"
	}
	
	map { arg ... args;
		server.sendMsg(14, nodeID, *(args.asControlInput)); //"/n_map"
	}
	mapMsg { arg ... args;
		^[14, nodeID] ++ args.asControlInput; //"/n_map"
	}	
	mapn { arg ... args;
		server.sendMsg(48, nodeID, *(args.asControlInput)); //"/n_mapn"
	}
	mapnMsg { arg ... args;
		^[48, nodeID] ++ args.asControlInput; //"/n_mapn"
	}
	// map to Bus objects
	busMap { arg firstControl, aBus ... args;
		var values;
		// busMap is not deprecated for multichannel buses
		//"busMap is deprecated, use map".warn;
		values = List.new;
		args.pairsDo({ arg control, bus; values.addAll([control, bus.index, bus.numChannels])});
		server.sendMsg(48, nodeID, firstControl, aBus.index, aBus.numChannels, *values);
		//"/n_mapn"
	}
	busMapMsg { arg firstControl, aBus ... args;
		var values;
		// busMap is not deprecated for multichannel buses
		//"busMapMsg is deprecated, use map".warn;
		values = List.new;
		args.pairsDo({ arg control, bus; values.addAll([control, bus.index, bus.numChannels])});
		^[48, nodeID, firstControl, aBus.index, aBus.numChannels] ++ values;
		//"/n_mapn"
	}
	
	set { arg ... args;
		server.sendMsg(15, nodeID, *(args.asOSCArgArray));  //"/n_set"
	}
	
	setMsg { arg ... args;
//		^[15, nodeID] ++ args.unbubble.asControlInput; 		^[15, nodeID] ++ args.asOSCArgArray; 	 //"/n_set"
	}

	setn { arg ... args;
		server.sendMsg(*this.setnMsg(*args));
	}

	*setnMsgArgs{ arg ... args;
		var nargs=List.new;
		args = args.asControlInput; 
		args.pairsDo { arg control, moreVals; 
			if(moreVals.isArray,{
				nargs.addAll([control, moreVals.size]++ moreVals);
			},{
				nargs.addAll([control, 1, moreVals]);
			});
		};		
		^nargs;
	}

	setnMsg { arg ... args;
		^[16, nodeID] ++ Node.setnMsgArgs(*args); 
		// "n_setn"
	}
		
	fill { arg controlName, numControls, value ... args;
		server.sendMsg(17, nodeID, controlName, numControls, value, *(args.asControlInput));//"n_fill"
	}
	
	fillMsg { arg controlName, numControls, value ... args;
		^[17, nodeID, controlName, numControls, value] ++ args.asControlInput; //"n_fill"
	}

	release { arg releaseTime;
		server.sendMsg(*this.releaseMsg(releaseTime))
    	}
    	releaseMsg { arg releaseTime;
		//assumes a control called 'gate' in the synth
		if(releaseTime.isNil, {
			releaseTime = 0.0;
		},{
			releaseTime = -1.0 - releaseTime;
		});
		^[15, nodeID, \gate, releaseTime]
	}
	trace {
		server.sendMsg(10, nodeID);//"/n_trace"
	}
	query {
		OSCresponder(server.addr,'/n_info',{ arg a,b,c;
			var cmd,argnodeID,parent,prev,next,isGroup,head,tail;
			# cmd,argnodeID,parent,prev,next,isGroup,head,tail = c;
			// assuming its me ... if(nodeID == argnodeID)
			Post << if(isGroup == 1, "Group:" , "Synth:") << nodeID << Char.nl
				<< "parent   : " << parent << Char.nl
				<< "prev : " << prev << Char.nl
				<< "next  :" << next << Char.nl;
			if(isGroup==1,{
				Post << "head :" << head << Char.nl
				 << "tail :" << tail << Char.nl << Char.nl;
			});
		}).add.removeWhenDone;
		server.sendMsg(46, nodeID)  //"/n_query"
	}
	register { arg assumePlaying=false;
		NodeWatcher.register(this, assumePlaying)
	}
	
	moveBefore { arg aNode;
		group = aNode.group; 
		server.sendMsg(18, nodeID, aNode.nodeID); //"/n_before"
	}
	moveAfter { arg aNode;
		group = aNode.group; 
		server.sendMsg(19, nodeID, aNode.nodeID); //"/n_after"
	}       
	moveToHead { arg aGroup;
		(aGroup ? server.defaultGroup).moveNodeToHead(this);
	}
	moveToTail { arg aGroup;
		(aGroup ? server.defaultGroup).moveNodeToTail(this);
	}

	// message creating methods
	
	moveBeforeMsg { arg aNode;
		group = aNode.group;
		^[18, nodeID, aNode.nodeID];//"/n_before"
	}
	moveAfterMsg { arg aNode;
		group = aNode.group;
		^[19, nodeID, aNode.nodeID]; //"/n_after"
	}       
	moveToHeadMsg { arg aGroup;
		^(aGroup ? server.defaultGroup).moveNodeToHeadMsg(this);
	}
	moveToTailMsg { arg aGroup;
		^(aGroup ? server.defaultGroup).moveNodeToTailMsg(this);
	}

	*orderNodesMsg { arg nodes;
		var msg = [18]; // "/n_after"
		nodes.doAdjacentPairs { |first, toMoveAfter|
			msg = msg.add(toMoveAfter.nodeID);
			msg = msg.add(first.nodeID);
		};
		^msg
	}

	hash {  ^server.hash bitXor: nodeID.hash	}
	
	== { arg aNode;
		^aNode respondsTo: #[\nodeID, \server] 
			and: { aNode.nodeID == nodeID and: { aNode.server === server }}
	}
	printOn { arg stream; stream << this.class.name << "(" << nodeID <<")" }
	asUGenInput { Error("should not use a % inside a SynthDef".format(this.class.name)).throw }
	asControlInput { ^this.nodeID }

}


Group : Node {
	
	/** immediately sends **/
	*new { arg target, addAction=\addToHead;
		var group, server, addNum, inTarget;
		inTarget = target.asTarget;
		server = inTarget.server;
		group = this.basicNew(server);
		addNum = addActions[addAction];
		if((addNum < 2), { group.group = inTarget; }, { group.group = inTarget.group; });
		server.sendMsg(21, group.nodeID, addNum, inTarget.nodeID);
		^group
	}
	newMsg { arg target, addAction = \addToHead;
		var addNum, inTarget;
		// if target is nil set to default group of server specified when basicNew was called
		inTarget = (target ? server.defaultGroup).asTarget;
		addNum = addActions[addAction];
		(addNum < 2).if({ group = inTarget; }, { group = inTarget.group; });
		^[21, nodeID, addNum, inTarget.nodeID]		//"/g_new"
	}
	*after { arg aNode;    ^this.new(aNode, \addAfter) }
	*before {  arg aNode; 	^this.new(aNode, \addBefore) }
	*head { arg aGroup; 	^this.new(aGroup, \addToHead) }
	*tail { arg aGroup; 	^this.new(aGroup, \addToTail) }
	*replace { arg nodeToReplace; ^this.new(nodeToReplace, \addReplace) }
	
	// for bundling
	addToHeadMsg { arg aGroup; 
		// if aGroup is nil set to default group of server specified when basicNew was called
		group = (aGroup ? server.defaultGroup);
		^[21, nodeID, 0, group.nodeID] 			//"/g_new"
	}
	addToTailMsg { arg aGroup;
		// if aGroup is nil set to default group of server specified when basicNew was called
		group = (aGroup ? server.defaultGroup);
		^[21, nodeID, 1, group.nodeID] 			//"/g_new"
	}
	addAfterMsg {  arg aNode;
		group = aNode.group;
		^[21, nodeID, 3, aNode.nodeID] 	//"/g_new"
	}
	addBeforeMsg {  arg aNode;
		group = aNode.group;
		^[21, nodeID, 2, aNode.nodeID] 	//"/g_new"
	}
	addReplaceMsg { arg nodeToReplace;
		group = nodeToReplace.group;
		^[21, nodeID, 4, nodeToReplace.nodeID] 	//"/g_new"
	}
	
	// move Nodes to this group
	moveNodeToHead { arg aNode;
		aNode.group = this;
		server.sendMsg(22, nodeID, aNode.nodeID); //"/g_head"
	}
	moveNodeToTail { arg aNode;
		aNode.group = this;
		server.sendMsg(23, nodeID, aNode.nodeID); //"/g_tail"
	}
	moveNodeToHeadMsg { arg aNode;
		aNode.group = this;
		^[22, nodeID, aNode.nodeID]; 			//"/g_head"
	}
	moveNodeToTailMsg { arg aNode;
		aNode.group = this;
		^[23, nodeID, aNode.nodeID];			//g_tail
	}
	
	freeAll {
		// free my children, but this node is still playing
		server.sendMsg(24, nodeID); //"/g_freeAll"
	}
	freeAllMsg {
		// free my children, but this node is still playing
		^[24, nodeID]; //"/g_freeAll"
	}
	deepFree {
		server.sendMsg(50, nodeID) //"/g_deepFree"
	}
	deepFreeMsg {
		^[50, nodeID] //"/g_deepFree"
	}
	
	// Introspection
	dumpTree { arg postControls = false;
		server.sendMsg("/g_dumpTree", nodeID, postControls.binaryValue)
	}

	queryTree { //|action|
		var resp, done = false;
		resp = OSCresponderNode(server.addr, '/g_queryTree.reply', { arg time, responder, msg;
			var i = 2, tabs = 0, printControls = false, dumpFunc;
			if(msg[1] != 0, {printControls = true});
			("NODE TREE Group" + msg[2]).postln;
			if(msg[3] > 0, {
				dumpFunc = {|numChildren|
					var j;
					tabs = tabs + 1;
					numChildren.do({
						if(msg[i + 1] >=0, {i = i + 2}, {
							i = i + 3 + if(printControls, {msg[i + 3] * 2 + 1}, {0});
						});
						tabs.do({ "   ".post });
						msg[i].post; // nodeID
						if(msg[i + 1] >=0, {
							" group".postln;
							if(msg[i + 1] > 0, { dumpFunc.value(msg[i + 1]) });
							}, {
								(" " ++ msg[i + 2]).postln; // defname
								if(printControls, {
									if(msg[i + 3] > 0, {
										" ".post;
										tabs.do({ "   ".post });
									});
									j = 0;
									msg[i + 3].do({
										" ".post;
										if(msg[i + 4 + j].isMemberOf(Symbol), {
											(msg[i + 4 + j] ++ ": ").post;
										});
										msg[i + 5 + j].post;
										j = j + 2;
									});
									"\n".post;
								});
							});		
					});
					tabs = tabs - 1;
				};
				dumpFunc.value(msg[3]);
			});
				
			//				action.value(msg);
			done = true;
		}).add.removeWhenDone;
		server.sendMsg("/g_queryTree", nodeID);
		SystemClock.sched(3, { 
			done.not.if({
				resp.remove;
				"Server failed to respond to Group:queryTree!".warn;
			}); 
		});
	}

//	queryTree { |action|
//		var resp, done = false;
//		resp = OSCresponderNode(server.addr, '/g_queryTree.reply', { arg time, responder, msg;
//				action.value(msg);
//				done = true;
//			}).add.removeWhenDone;
//		server.sendMsg("/g_queryTree", nodeID);
//		SystemClock.sched(3, { 
//			done.not.if({
//				resp.remove;
//				"Server failed to respond to Group:queryTree!".warn;
//			}); 
//		});
//	}
	
}

Synth : Node {

	var <>defName;
	
	/** immediately sends **/
	*new { arg defName, args, target, addAction=\addToHead;
		var synth, server, addNum, inTarget;
		inTarget = target.asTarget;
		server = inTarget.server;
		addNum = addActions[addAction];
		synth = this.basicNew(defName, server);

		if((addNum < 2), { synth.group = inTarget; }, { synth.group = inTarget.group; });
//		server.sendMsg(59, //"s_newargs"
//			defName, synth.nodeID, addNum, inTarget.nodeID,
//			*Node.setnMsgArgs(*args));
		server.sendMsg(9, //"s_new"
			defName, synth.nodeID, addNum, inTarget.nodeID,
			*(args.asOSCArgArray)
		);
		^synth
	}
	*newPaused { arg defName, args, target, addAction=\addToHead;
		var synth, server, addNum, inTarget;
		inTarget = target.asTarget;
		server = inTarget.server;
		addNum = addActions[addAction];
		synth = this.basicNew(defName, server);
		if((addNum < 2), { synth.group = inTarget; }, { synth.group = inTarget.group; });
		server.sendBundle(nil, [9, defName, synth.nodeID, addNum, inTarget.nodeID] ++ 
			args.asControlInput, [12, synth.nodeID, 0]); // "s_new" + "/n_run"
		^synth
	}
		/** does not send	(used for bundling) **/
	*basicNew { arg defName, server, nodeID;
		^super.basicNew(server, nodeID).defName_(defName.asDefName)
	}
	
	newMsg { arg target, args, addAction = \addToHead;
		var addNum, inTarget;
		addNum = addActions[addAction];
		// if target is nil set to default group of server specified when basicNew was called
		inTarget = (target ? server.defaultGroup).asTarget;
		(addNum < 2).if({ group = inTarget; }, { group = inTarget.group; });
		^[9, defName, nodeID, addNum, inTarget.nodeID] ++ args.asControlInput; //"/s_new"
	}
	*after { arg aNode, defName, args;	
		^this.new(defName, args, aNode, \addAfter);
	}
	*before {  arg aNode, defName, args;
		^this.new(defName, args, aNode, \addBefore); 
	}
	*head { arg aGroup, defName, args; 
		^this.new(defName, args, aGroup, \addToHead); 
	}
	*tail { arg aGroup, defName, args; 
		^this.new(defName, args, aGroup, \addToTail);  
	}
	*replace { arg nodeToReplace, defName, args;
		^this.new(defName, args, nodeToReplace, \addReplace)
	}
	// for bundling
	addToHeadMsg { arg aGroup, args;
		// if aGroup is nil set to default group of server specified when basicNew was called
		group = (aGroup ? server.defaultGroup);
		^[9, defName, nodeID, 0, group.nodeID] ++ args.asControlInput	// "/s_new"
	}
	addToTailMsg { arg aGroup, args;
		// if aGroup is nil set to default group of server specified when basicNew was called
		group = (aGroup ? server.defaultGroup);
		^[9, defName, nodeID, 1, group.nodeID] ++ args.asControlInput // "/s_new"
	}
	addAfterMsg {  arg aNode, args;
		group = aNode.group; 
		^[9, defName, nodeID, 3, aNode.nodeID] ++ args.asControlInput // "/s_new"
	}
	addBeforeMsg {  arg aNode, args;
		group = aNode.group; 
		^[9, defName, nodeID, 2, aNode.nodeID] ++ args.asControlInput // "/s_new"
	}
	addReplaceMsg { arg nodeToReplace, args;
		group = nodeToReplace.group; 
		^[9, defName, nodeID, 4, nodeToReplace.nodeID] ++ args.asControlInput // "/s_new"
	}
	
	// nodeID -1 
	*grain { arg defName, args, target, addAction=\addToHead;
		var server;
		target = target.asTarget;
		server = target.server;
		server.sendMsg(9, defName.asDefName, -1, addActions[addAction], target.nodeID, 
			*(args.asControlInput)); //"/s_new"
		^nil;
	}
	
	get { arg index, action;
		OSCpathResponder(server.addr,['/n_set',nodeID,index],{ arg time, r, msg; 
			action.value(msg.at(3)); r.remove }).add;
		server.sendMsg(44, nodeID, index);	//"/s_get"
	}
	getMsg { arg index;
		^[44, nodeID, index];	//"/s_get"
	}
	
	getn { arg index, count, action;
		OSCpathResponder(server.addr,['/n_setn',nodeID,index],{arg time, r, msg;
			action.value(msg.copyToEnd(4)); r.remove } ).add; 
		server.sendMsg(45, nodeID, index, count); //"/s_getn"
	}
	getnMsg { arg index, count;
		^[45, nodeID, index, count]; //"/s_getn"
	}
			
	printOn { arg stream; stream << this.class.name << "(" <<< defName << " : " << nodeID <<")" }

}

RootNode : Group {
	
	classvar <roots;
	
	*new { arg server;
		server = server ? Server.default;
		^(roots.at(server.name) ?? {
			^super.basicNew(server, 0).rninit
		})
	}
	rninit { 
		roots.put(server.name, this);
		isPlaying = isRunning = true;
		group = this; // self
	}
	*initClass {  roots = IdentityDictionary.new; }

	run { "run has no effect on RootNode".warn; }
	free { "free has no effect on RootNode".warn; }
	moveBefore { "moveBefore has no effect on RootNode".warn; }
	moveAfter { "moveAfter has no effect on RootNode".warn; }
	moveToHead { "moveToHead has no effect on RootNode".warn; }
	moveToTail{ "moveToTail has no effect on RootNode".warn; }

	*freeAll {
		roots.do({ arg rn; rn.freeAll })
	}
} 

