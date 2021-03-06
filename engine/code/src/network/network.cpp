//       _________ __                 __
//      /   _____//  |_____________ _/  |______     ____  __ __  ______
//      \_____  \\   __\_  __ \__  \\   __\__  \   / ___\|  |  \/  ___/
//      /        \|  |  |  | \// __ \|  |  / __ \_/ /_/  >  |  /\___ |
//     /_______  /|__|  |__|  (____  /__| (____  /\___  /|____//____  >
//             \/                  \/          \//_____/            \/
//  ______________________                           ______________________
//                        T H E   W A R   B E G I N S
//         Stratagus - A free fantasy real time strategy game engine
//
/**@name network.cpp - The network. */
//
//      (c) Copyright 2000-2006 by Lutz Sammer, Andreas Arens, and Jimmy Salmon
//
//      This program is free software; you can redistribute it and/or modify
//      it under the terms of the GNU General Public License as published by
//      the Free Software Foundation; only version 2 of the License.
//
//      This program is distributed in the hope that it will be useful,
//      but WITHOUT ANY WARRANTY; without even the implied warranty of
//      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//      GNU General Public License for more details.
//
//      You should have received a copy of the GNU General Public License
//      along with this program; if not, write to the Free Software
//      Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
//      02111-1307, USA.
//
//      $Id: network.cpp 7784 2006-11-26 20:58:20Z jsalmon3 $

//@{

//----------------------------------------------------------------------------
// Documentation
//----------------------------------------------------------------------------

/**
** @page NetworkModule Module - Network
**
** @section Basics How does it work.
**
** Stratagus uses an UDP peer to peer protocol (p2p). The default port
** is 6660.
**
** @subsection udp_vs_tcp UDP vs. TCP
**
** UDP is a connectionless protocol. This means it does not perform
** retransmission of data and therefore provides very few error recovery
** services. UDP instead offers a direct way to send and receive
** datagrams (packets) over the network; it is used primarily for
** broadcasting messages.
**
** TCP, on the other hand, provides a connection-based, reliable data
** stream.  TCP guarantees delivery of data and also guarantees that
** packets will be delivered in the same order in which they were sent.
**
** TCP is a simple and effective way of transmitting data. For making sure
** that a client and server can talk to each other it is very good.
** However, it carries with it a lot of overhead and extra network lag.
**
** UDP needs less overhead and has a smaller lag. Which is very important
** for real time games. The disadvantages includes:
**
** @li You won't have an individual socket for each client.
** @li Given that clients don't need to open a unique socket in order to
** transmit data there is the very real possibility that a client
** who is not logged into the game will start sending all kinds of
** garbage to your server in some kind of attack. It becomes much
** more difficult to stop them at this point.
** @li Likewise, you won't have a clear disconnect/leave game message
** unless you write one yourself.
** @li Some data may not reach the other machine, so you may have to send
** important stuff many times.
** @li Some data may arrive in the wrong order. Imagine that you get
** package 3 before package 1. Even a package can come duplicate.
** @li UDP is connectionless and therefore has problems with firewalls.
**
** I have choosen UDP. Additional support for the TCP protocol is welcome.
**
** @subsection sc_vs_p2p server/client vs. peer to peer
**
** @li server to client
**
** The player input is send to the server. The server collects the input
** of all players and than send the commands to all clients.
**
** @li peer to peer (p2p)
**
** The player input is direct send to all others clients in game.
**
** p2p has the advantage of a smaller lag, but needs a higher bandwidth
** by the clients.
**
** I have choosen p2p. Additional support for a server to client protocol
** is welcome.
**
** @subsection bandwidth bandwidth
**
** I wanted to support up to 8 players with 28.8kbit modems.
**
** Most modems have a bandwidth of 28.8K bits/second (both directions) to
** 56K bits/second (33.6K uplink) It takes actually 10 bits to send 1 byte.
** This makes calculating how many bytes you are sending easy however, as
** you just need to divide 28800 bits/second by 10 and end up with 2880
** bytes per second.
**
** We want to send many packets, more updated pro second and big packets,
** less protocol overhead.
**
** If we do an update 6 times per second, leaving approximately 480 bytes
** per update in an ideal environment.
**
** For the TCP/IP protocol we need following:
** IP  Header 20 bytes
** UDP Header 8  bytes
**
** With 10 bytes per command and 4 commands this are 68 (20+8+4*10) bytes
** pro packet.  Sending it to 7 other players, gives 476 bytes pro update.
** This means we could do 6 updates (each 166ms) pro second.
**
** @subsection a_packet Network packet
**
** @li [IP  Header - 20 bytes]
** @li [UDP Header -  8 bytes]
** @li [Type 1 byte][Cycle 1 byte][Data 8 bytes] - Slot 0
** @li [Type 1 byte][Cycle 1 byte][Data 8 bytes] - Slot 1
** @li [Type 1 byte][Cycle 1 byte][Data 8 bytes] - Slot 2
** @li [Type 1 byte][Cycle 1 byte][Data 8 bytes] - Slot 3
**
** @subsection internals Putting it together
**
** All computers in play must run absolute syncron. Only user commands
** are send over the network to the other computers. The command needs
** some time to reach the other clients (lag), so the command is not
** executed immediatly on the local computer, it is stored in a delay
** queue and send to all other clients. After a delay of ::NetworkLag
** game cycles the commands of the other players are received and executed
** together with the local command. Each ::NetworkUpdates game cycles there
** must a package send, to keep the clients in sync, if there is no user
** command, a dummy sync package is send.
** If there are missing packages, the game is paused and old commands
** are resend to all clients.
**
** @section missing What features are missing
**
** @li The recover from lost packets can be improved, if the server knows
** which packets the clients have received.
**
** @li The UDP protocol isn't good for firewalls, we need also support
** for the TCP protocol.
**
** @li Add a server / client protocol, which allows more players pro
** game.
**
** @li Lag (latency) and bandwidth are set over the commandline. This
** should be automatic detected during game setup and later during
** game automatic adapted.
**
** @li Also it would be nice, if we support viewing clients. This means
** other people can view the game in progress.
**
** @li The current protocol only uses single cast, for local LAN we
** should also support broadcast and multicast.
**
** @li Proxy and relays should be supported, to improve the playable
** over the internet.
**
** @li The game cycles is transfered for each slot, this is not needed. We
** can save some bytes if we compress this.
**
** @li We can sort the command by importants, currently all commands are
** send in order, only chat messages are send if there are free slots.
**
** @li password protection the login process (optional), to prevent that
** the wrong player join an open network game.
**
** @li add meta server support, i have planned to use bnetd and its
** protocol.
**
** @section api API How should it be used.
**
** ::InitNetwork1()
**
** ::InitNetwork2()
**
** ::ExitNetwork1()
**
** ::NetworkSendCommand()
**
** ::NetworkSendExtendedCommand()
**
** ::NetworkEvent()
**
** ::NetworkQuit()
**
** ::NetworkChatMessage()
**
** ::NetworkEvent()
**
** ::NetworkRecover()
**
** ::NetworkCommands()
**
** ::NetworkFildes
**
** ::NetworkInSync
**
** @todo FIXME: continue docu
*/

//----------------------------------------------------------------------------
//  Includes
//----------------------------------------------------------------------------

#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>
#include <string.h>

#include "stratagus.h"

#include "net_lowlevel.h"
#include "unit.h"
#include "unittype.h"
#include "map.h"
#include "actions.h"
#include "player.h"
#include "network.h"
#include "netconnect.h"
#include "commands.h"
#include "interface.h"
#include "results.h"
#include "master.h"


//----------------------------------------------------------------------------
//  Declaration
//----------------------------------------------------------------------------

/**
**  Network command input/output queue.
*/
struct NetworkCommandQueue {
	unsigned long Time;     /// time to execute
	unsigned char Type;     /// Command Type
	NetworkCommand Data;    /// command content
};

//----------------------------------------------------------------------------
//  Variables
//----------------------------------------------------------------------------

int NetworkNumInterfaces;                  /// Network number of interfaces
Socket NetworkFildes = (Socket)-1;         /// Network file descriptor
int NetworkInSync = 1;                     /// Network is in sync
int NetworkUpdates = 5;                    /// Network update each # game cycles
int NetworkLag = 10;                       /// Network lag in # game cycles
unsigned long NetworkStatus[PlayerMax];    /// Network status
unsigned long NetworkLastFrame[PlayerMax]; /// Last frame received packet
int NetworkTimeout = 45;                   /// Number of seconds until player times out

static char NetMsgBuf[PlayerMax][128];     /// Chat message buffers
static int NetMsgBufLen[PlayerMax];        /// Stored chat message length
#ifdef DEBUG
unsigned long MyHost;                      /// My host number.
int MyPort;                                /// My port number.
#endif
static unsigned long NetworkDelay;         /// Delay counter for recover.
static int NetworkSyncSeeds[256];          /// Network sync seeds.
static int NetworkSyncHashs[256];          /// Network sync hashs.
static NetworkCommandQueue NetworkIn[256][PlayerMax][MaxNetworkCommands]; /// Per-player network packet input queue
std::list<NetworkCommandQueue *> CommandsIn;   /// Network command input queue
std::list<NetworkCommandQueue *> MsgCommandsIn;/// Network message input queue

#ifdef DEBUG
static int NetworkReceivedPackets;         /// Packets received packets
static int NetworkReceivedEarly;           /// Packets received too early
static int NetworkReceivedLate;            /// Packets received too late
static int NetworkReceivedDups;            /// Packets received as duplicates
static int NetworkReceivedLost;            /// Packets received packet lost

static int NetworkSendPackets;             /// Packets send packets
static int NetworkSendResend;              /// Packets send to resend
#endif

static int PlayerQuit[PlayerMax];          /// Player quit

#define MAX_NCQS 100
static NetworkCommandQueue NCQs[MAX_NCQS]; /// NetworkCommandQueues
static int NumNCQs;                        /// Number of NCQs in use


//----------------------------------------------------------------------------
//  Mid-Level api functions
//----------------------------------------------------------------------------

/**
**  Send message to all clients.
**
**  @param buf  Buffer of outgoing message.
**  @param len  Buffer length.
*/
void NetworkBroadcast(const void *buf, int len)
{
	// Send to all clients.
	for (int i = 0; i < HostsCount; ++i) {
		NetSendUDP(NetworkFildes, Hosts[i].Host, Hosts[i].Port, buf, len);
	}
}

/**
**  Network send packet. Build it from queue and broadcast.
**
**  @param ncq  Outgoing network queue start.
*/
static void NetworkSendPacket(const NetworkCommandQueue *ncq)
{
	NetworkPacket packet;
	int i;
	int numcommands;

#ifdef DEBUG
	++NetworkSendPackets;
#endif

	//
	// Build packet of up to MaxNetworkCommands messages.
	//
	numcommands = 0;
	packet.Header.Cycle = ncq[0].Time & 0xFF;
	for (i = 0; i < MaxNetworkCommands && ncq[i].Type != MessageNone; ++i) {
		packet.Header.Type[i] = ncq[i].Type;
		packet.Command[i] = ncq[i].Data;
		++numcommands;
	}

	for (; i < MaxNetworkCommands; ++i) {
		packet.Header.Type[i] = MessageNone;
	}

	NetworkBroadcast(&packet, sizeof(NetworkPacketHeader) +
		sizeof(NetworkCommand) * numcommands);
}

//----------------------------------------------------------------------------
//  API init..
//----------------------------------------------------------------------------

/**
**  Initialize network part 1.
*/
void InitNetwork1(void)
{
	int i;
	int port;

	DebugPrint("\n");

	NetworkFildes = (Socket)-1;
	NetworkInSync = 1;
	NetworkNumInterfaces = 0;

	NetInit(); // machine dependent setup

	for (i = 0; i < PlayerMax; ++i) {
		NetMsgBufLen[i] = 0;
	}

	if (NetworkUpdates <= 0) {
		NetworkUpdates = 1;
	}
	// Lag must be multiple of updates
	NetworkLag = (NetworkLag / NetworkUpdates) * NetworkUpdates;

	// Our communication port
	port = NetworkPort;
	for (i = 0; i < 10; ++i) {
		NetworkFildes = NetOpenUDP(port + i);
		if (NetworkFildes != (Socket)-1) {
			break;
		}
	}
	if (i == 10) {
		fprintf(stderr, "NETWORK: No free ports %d-%d available, aborting\n",
			port, port + i);
		NetExit(); // machine dependent network exit
		return;
	}

#if 1
	// FIXME: need a working interface check
	NetworkNumInterfaces = 1;
#else
	NetworkNumInterfaces = NetSocketAddr(NetworkFildes);
	if (NetworkNumInterfaces) {
		DebugPrint("Num IP: %d\n" _C_ NetworkNumInterfaces);
		for (i = 0; i < NetworkNumInterfaces; ++i) {
			DebugPrint("IP: %d.%d.%d.%d\n" _C_ NIPQUAD(ntohl(NetLocalAddrs[i])));
		}
	} else {
		fprintf(stderr, "NETWORK: Not connected to any external IPV4-network, aborting\n");
		ExitNetwork1();
		return;
	}
#endif

#ifdef DEBUG
	{
		char buf[128];

		gethostname(buf, sizeof(buf));
		DebugPrint("%s\n" _C_ buf);
		MyHost = NetResolveHost(buf);
		MyPort = NetLastPort;
		DebugPrint("My host:port %d.%d.%d.%d:%d\n" _C_
			NIPQUAD(ntohl(MyHost)) _C_ ntohs(MyPort));
	}
#endif

	CommandsIn.clear();
	MsgCommandsIn.clear();

	NumNCQs = 0;
}

/**
**  Cleanup network part 1. (to be called _AFTER_ part 2 :)
*/
void ExitNetwork1(void)
{
	if (!IsNetworkGame()) { // No network running
		return;
	}

#ifdef DEBUG
	DebugPrint("Received: %d packets, %d early, %d late, %d dups, %d lost.\n" _C_
		NetworkReceivedPackets _C_ NetworkReceivedEarly _C_ NetworkReceivedLate _C_
		NetworkReceivedDups _C_ NetworkReceivedLost);
	DebugPrint("Send: %d packets, %d resend\n" _C_
		NetworkSendPackets _C_ NetworkSendResend);
#endif

	NetCloseUDP(NetworkFildes);
	NetExit(); // machine dependent setup

	NetworkFildes = (Socket)-1;
	NetworkInSync = 1;
	NetPlayers = 0;
	HostsCount = 0;
}

/**
**  Initialize network part 2.
*/
void InitNetwork2(void)
{
	NetworkConnectSetupGame();

	DebugPrint("Lag %d, Updates %d, Hosts %d\n" _C_
		NetworkLag _C_ NetworkUpdates _C_ HostsCount);

	//
	// Prepare first time without syncs.
	//
	memset(NetworkIn, 0, sizeof(NetworkIn));
	for (int i = 0; i <= NetworkLag; i += NetworkUpdates) {
		for (int n = 0; n < HostsCount; ++n) {
			for (int c = 0; c < MaxNetworkCommands; ++c) {
				NetworkIn[i][Hosts[n].PlyNr][c].Time = i;
				NetworkIn[i][Hosts[n].PlyNr][c].Type = MessageSync;
			}
		}
	}

	memset(NetworkSyncSeeds, 0, sizeof(NetworkSyncSeeds));
	memset(NetworkSyncHashs, 0, sizeof(NetworkSyncHashs));
	memset(PlayerQuit, 0, sizeof(PlayerQuit));
	memset(NetworkStatus, 0, sizeof(NetworkStatus));
	memset(NetworkLastFrame, 0, sizeof(NetworkLastFrame));
}

//----------------------------------------------------------------------------
//  Memory management for NetworkCommandQueues
//----------------------------------------------------------------------------

/**
**  Allocate a NetworkCommandQueue
**
**  @return  NetworkCommandQueue
*/
static NetworkCommandQueue *AllocNCQ(void)
{
	Assert(NumNCQs != MAX_NCQS);
	NetworkCommandQueue *ncq = &NCQs[NumNCQs++];
	memset(ncq, 0, sizeof(*ncq));
	return ncq;
}

/**
**  Free a NetworkCommandQueue
**
**  @param ncq  NetworkCommandQueue to free
*/
static void FreeNCQ(NetworkCommandQueue *ncq)
{
	NCQs[ncq - NCQs] = NCQs[--NumNCQs];
}

//----------------------------------------------------------------------------
//  Commands input
//----------------------------------------------------------------------------

/**
**  Prepare send of command message.
**
**  Convert arguments into network format and place it into output queue.
**
**  @param command  Command (Move,Attack,...).
**  @param unit     Unit that receive the command.
**  @param x        optional X map position.
**  @param y        optional y map position.
**  @param dest     optional destination unit.
**  @param type     optional unit-type argument.
**  @param status   Append command or flush old commands.
**
**  @warning  Destination and unit-type shares the same network slot.
*/
void NetworkSendCommand(int command, const CUnit *unit, int x, int y,
	const CUnit *dest, const CUnitType *type, int status)
{
	NetworkCommandQueue *ncq;
	std::list<NetworkCommandQueue *>::iterator it;

	// Check for duplicate command in queue
	for (it = CommandsIn.begin(); it != CommandsIn.end(); ++it) {
		ncq = *it;
		if ((ncq->Type & 0x7F) == command &&
				ncq->Data.Unit == htons(unit->Slot) &&
				ncq->Data.X == htons(x) &&
				ncq->Data.Y == htons(y)) {
			if (dest && ncq->Data.Dest == htons(dest->Slot)) {
				return;
			} else if (type && ncq->Data.Dest == htons(type->Slot)) {
				return;
			} else if (ncq->Data.Dest == 0xFFFF) {
				return;
			}
		}
	}

	ncq = AllocNCQ();
	CommandsIn.push_back(ncq);

	ncq->Time = GameCycle;
	ncq->Type = command;
	if (status) {
		ncq->Type |= 0x80;
	}
	ncq->Data.Unit = htons(unit->Slot);
	ncq->Data.X = htons(x);
	ncq->Data.Y = htons(y);
	Assert (!dest || !type); // Both together isn't allowed
	if (dest) {
		ncq->Data.Dest = htons(dest->Slot);
	} else if (type) {
		ncq->Data.Dest = htons(type->Slot);
	} else {
		ncq->Data.Dest = htons(0xFFFF); // -1
	}

}

/**
**  Prepare send of extended command message.
**
**  Convert arguments into network format and place it into output queue.
**
**  @param command  Command (Move,Attack,...).
**  @param arg1     optional argument #1
**  @param arg2     optional argument #2
**  @param arg3     optional argument #3
**  @param arg4     optional argument #4
**  @param status   Append command or flush old commands.
*/
void NetworkSendExtendedCommand(int command, int arg1, int arg2, int arg3,
	int arg4, int status)
{
	NetworkCommandQueue *ncq;
	NetworkExtendedCommand *nec;

	ncq = AllocNCQ();
	CommandsIn.push_back(ncq);

	ncq->Time = GameCycle;
	nec = (NetworkExtendedCommand *)&ncq->Data;

	ncq->Type = MessageExtendedCommand;
	if (status) {
		ncq->Type |= 0x80;
	}
	nec->ExtendedType = command;
	nec->Arg1 = arg1;
	nec->Arg2 = htons(arg2);
	nec->Arg3 = htons(arg3);
	nec->Arg4 = htons(arg4);
}

/**
**  Sends my selections to teammates
**
**  @param units  Units to send
**  @param count  Number of units to send
*/
void NetworkSendSelection(CUnit **units, int count)
{
	static NetworkPacket packet;
	NetworkSelectionHeader *header;
	NetworkSelection *selection;
	int unitcount;
	int ref;
	int i;
	int teammates[PlayerMax];
	int numteammates;
	int nosent;

	// Check if we have any teammates to send to
	numteammates = 0;
	for (i = 0; i < HostsCount; ++i) {
		if (Players[Hosts[i].PlyNr].Team == ThisPlayer->Team) {
			teammates[numteammates++] = i;
		}
	}
	if (!numteammates) {
		return;
	}

	//
	//  Build and send packets to cover all units.
	//
	unitcount = 0;
	while (unitcount < count) {
		header = (NetworkSelectionHeader *)&(packet.Header);
		if (unitcount == 0) {
			header->Add = 0;
		} else {
			header->Add = 1;
		}
		header->Remove = 0;

		nosent = 0;
		for (i = 0; i < MaxNetworkCommands && unitcount < count; ++i) {
			header->Type[i] = MessageSelection;
			selection = (NetworkSelection *)&packet.Command[i];
			for (ref = 0; ref < 4 && unitcount < count; ++ref, ++unitcount) {
				selection->Unit[ref] = htons(UnitNumber(units[unitcount]));
				++nosent;
			}
		}

		if (unitcount >= count) {
			// This is the last command
			header->NumberSent = nosent;
		} else {
			header->NumberSent = MaxNetworkCommands * 4;
		}

		for (; i < MaxNetworkCommands; ++i) {
			packet.Header.Type[i] = MessageNone;
		}
		

		//
		// Send the Constructed packet to team members
		//
		for (i = 0; i < numteammates; ++i) {
			ref = NetSendUDP(NetworkFildes, Hosts[teammates[i]].Host, Hosts[teammates[i]].Port,
				&packet, sizeof(NetworkPacketHeader) + sizeof(NetworkSelection) * ((nosent + 3) / 4));
		}
	}

}
/**
**  Process Received Unit Selection
**
**  @param packet  Network Packet to Process
**  @param player  Player number
*/
static void NetworkProcessSelection(NetworkPacket *packet, int player)
{
	CUnit *units[UnitMax];
	NetworkSelectionHeader *header;
	NetworkSelection *selection;
	int adjust;
	int count;
	int unitcount;

	header = (NetworkSelectionHeader *)&(packet->Header);
	//
	// Create Unit Array
	//
	count = header->NumberSent;
	adjust = (header->Add << 1) | header->Remove;
	unitcount = 0;

	for (int i = 0; header->Type[i] == MessageSelection; ++i) {
		selection = (NetworkSelection *)&(packet->Command[i]);
		for (int j = 0; j < 4 && unitcount < count; ++j) {
			units[unitcount++] = Units[ntohs(selection->Unit[j])];
		}
	}
	Assert(count == unitcount);

	ChangeTeamSelectedUnits(&Players[player], units, adjust, count);
}

/**
**  Remove a player from the game.
**
**  @param player  Player number
*/
static void NetworkRemovePlayer(int player)
{
	int i;

	// Remove player from Hosts and clear NetworkIn
	for (i = 0; i < HostsCount; ++i) {
		if (Hosts[i].PlyNr == player) {
			Hosts[i] = Hosts[HostsCount - 1];
			--HostsCount;
			break;
		}
	}
	for (i = 0; i < 256; ++i) {
		for (int c = 0; c < MaxNetworkCommands; ++c) {
			NetworkIn[i][player][c].Time = 0;
		}
	}
}

/**
**  Called if message for the network is ready.
**  (by WaitEventsOneFrame)
**
**  @todo
**  NetworkReceivedEarly NetworkReceivedLate NetworkReceivedDups
**  Must be calculated.
*/
void NetworkEvent(void)
{
	char buf[1024];
	NetworkPacket *packet;
	int player;
	int i;
	int commands;
	bool allowed;
	unsigned long n;

	if (!IsNetworkGame()) {
		NetworkInSync = 1;
		return;
	}
	//
	// Read the packet.
	//
	if ((i = NetRecvUDP(NetworkFildes, &buf, sizeof(buf))) < 0) {
		//
		// Server or client gone?
		//
		DebugPrint("Server/Client gone?\n");
		// just hope for an automatic recover right now..
		NetworkInSync = 0;
		return;
	}

	packet = (NetworkPacket *)buf;
#ifdef DEBUG
	++NetworkReceivedPackets;
#endif

	//
	// Setup messages
	//
	if (NetConnectRunning) {
		if (NetworkParseSetupEvent(buf, i)) {
			return;
		}
	}

	//
	// Minimal checks for good/correct packet.
	//
	commands = 0;
	while (commands < MaxNetworkCommands && packet->Header.Type[commands] != MessageNone ) {
		++commands;
	}
	// Typecast to fix Broken GCC!! AH
	if (i != (int)(sizeof(NetworkPacketHeader) + sizeof(NetworkCommand) * commands)) {
		DebugPrint("Bad packet read:%d, expected:%d\n" _C_
			i _C_ (int)(sizeof(NetworkPacketHeader) + sizeof(NetworkCommand) * commands));
		return;
	}

	for (i = 0; i < HostsCount; ++i) {
		if (Hosts[i].Host == NetLastHost && Hosts[i].Port == NetLastPort &&
				!PlayerQuit[Hosts[i].PlyNr]) {
			break;
		}
	}
	if (i == HostsCount) {
		DebugPrint("Not a host in play: %d.%d.%d.%d:%d\n" _C_
				NIPQUAD(ntohl(NetLastHost)) _C_ ntohs(NetLastPort));
		return;
	}
	player = Hosts[i].PlyNr;

	// In a normal packet there is a least sync, selection may not have that
	if (packet->Header.Type[0] == MessageSelection || commands == 0) {
		NetworkProcessSelection(packet, player);
		return;
	}

	//
	// Parse the packet commands.
	//
	for (i = 0; i < commands; ++i) {
		const NetworkCommand *nc;

		nc = &packet->Command[i];

		//
		// Handle some messages.
		//
		if (packet->Header.Type[i] == MessageQuit) {
			PlayerQuit[nc->X] = 1;
		}

		if (packet->Header.Type[i] == MessageResend) {
			// Destination cycle (time to execute).
			n = ((GameCycle + 128) & ~0xFF) | packet->Header.Cycle;
			if (n > GameCycle + 128) {
				n -= 0x100;
			}

			// FIXME: not necessary to send this packet multiple times!!!!
			// other side sends re-send until it gets an answer.

			if (n != NetworkIn[n & 0xFF][ThisPlayer->Index][0].Time) {
				// Asking for a cycle we haven't gotten to yet, ignore for now
				return;
			}

			NetworkSendPacket(NetworkIn[n & 0xFF][ThisPlayer->Index]);

			// Check if a player quit this cycle
			for (int j = 0; j < HostsCount; ++j) {
				for (int c = 0; c < MaxNetworkCommands; ++c) {
					NetworkCommandQueue *ncq;
					ncq = &NetworkIn[n & 0xFF][Hosts[j].PlyNr][c];
					if (ncq->Time && ncq->Type == MessageQuit) {
						NetworkPacket np;
						np.Header.Cycle = ncq->Time & 0xFF;
						np.Header.Type[0] = ncq->Type;
						np.Command[0] = ncq->Data;
						for (int k = 1; k < MaxNetworkCommands; ++k) {
							np.Header.Type[k] = MessageNone;
						}

						NetworkBroadcast(&np, sizeof(NetworkPacketHeader) + sizeof(NetworkCommand));
					}
				}
			}

			return;
		}

		// Destination cycle (time to execute).
		n = ((GameCycle + 128) & ~0xFF) | packet->Header.Cycle;
		if (n > GameCycle + 128) {
			n -= 0x100;
		}

		// Receive statistic
		if (n > NetworkStatus[player]) {
			NetworkStatus[player] = n;
		}
		NetworkLastFrame[player] = FrameCounter;

		// Place in network in
		switch (packet->Header.Type[i] & 0x7F) {
			case MessageExtendedCommand:
				// FIXME: ensure the sender is part of the command
				allowed = true;
				break;
			case MessageSync:
				// Sync does not matter
				allowed = true;
				break;
			case MessageQuit:
			case MessageQuitAck:
			case MessageResend:
			case MessageChat:
			case MessageChatTerm:
				// FIXME: ensure it's from the right player
				allowed = true;
				break;
			case MessageCommandDismiss:
				// Allow to explode critters.
				if ((UnitSlots[ntohs(nc->Unit)]->Player->Index == PlayerNumNeutral) &&
					UnitSlots[ntohs(nc->Unit)]->Type->ClicksToExplode) {
					allowed = true;
					break;
				}
				// Fall through!
			default:
				if (UnitSlots[ntohs(nc->Unit)]->Player->Index == player ||
						Players[player].IsTeamed(UnitSlots[ntohs(nc->Unit)])) {
					allowed = true;
				} else {
					allowed = false;
				}
		}

		if (allowed) {
			NetworkIn[packet->Header.Cycle][player][i].Time = n;
			NetworkIn[packet->Header.Cycle][player][i].Type = packet->Header.Type[i];
			NetworkIn[packet->Header.Cycle][player][i].Data = *nc;
		} else {
			SetMessage(_("%s sent bad command"), Players[player].Name.c_str());
		}
	}

	for ( ; i < MaxNetworkCommands; ++i) {
		NetworkIn[packet->Header.Cycle][player][i].Time = 0;
	}

	//
	// Waiting for this time slot
	//
	if (!NetworkInSync) {
		NetworkInSync = 1;
		n = (GameCycle / NetworkUpdates) * NetworkUpdates + NetworkUpdates;
		for (player = 0; player < HostsCount; ++player) {
			if (NetworkIn[n & 0xFF][Hosts[player].PlyNr][0].Time != n) {
				NetworkInSync = 0;
				break;
			}
		}
	}
}

/**
**  Quit the game.
*/
void NetworkQuit(void)
{
	if (!ThisPlayer) {
		return;
	}

	int n = (GameCycle + NetworkUpdates) / NetworkUpdates * NetworkUpdates + NetworkLag;
	NetworkIn[n & 0xFF][ThisPlayer->Index][0].Type = MessageQuit;
	NetworkIn[n & 0xFF][ThisPlayer->Index][0].Time = n;
	NetworkIn[n & 0xFF][ThisPlayer->Index][0].Data.X = ThisPlayer->Index;

	for (int i = 1; i < MaxNetworkCommands; ++i) {
		NetworkIn[n & 0xFF][ThisPlayer->Index][i].Type = MessageNone;
	}

	NetworkSendPacket(NetworkIn[n & 0xFF][ThisPlayer->Index]);
}

/**
**  Send chat message. (Message is sent with low priority)
**
**  @param msg  Text message to send.
*/
void NetworkChatMessage(const std::string &msg)
{
	NetworkCommandQueue *ncq;
	NetworkChat *ncm;
	const char *cp;
	int n;

	if (IsNetworkGame()) {
		cp = msg.c_str();
		n = msg.size();
		while (n >= (int)sizeof(ncm->Text)) {
			ncq = AllocNCQ();
			MsgCommandsIn.push_back(ncq);
			ncq->Type = MessageChat;
			ncm = (NetworkChat *)(&ncq->Data);
			ncm->Player = ThisPlayer->Index;
			memcpy(ncm->Text, cp, sizeof(ncm->Text));
			cp += sizeof(ncm->Text);
			n -= sizeof(ncm->Text);
		}
		ncq = AllocNCQ();
		MsgCommandsIn.push_back(ncq);
		ncq->Type = MessageChatTerm;
		ncm = (NetworkChat *)(&ncq->Data);
		ncm->Player = ThisPlayer->Index;
		memcpy(ncm->Text, cp, n + 1); // see >= above :)
	}
}

/**
**  Parse a network command.
**
**  @param ncq  Network command from queue
*/
static void ParseNetworkCommand(const NetworkCommandQueue *ncq)
{
	int ply;

	switch (ncq->Type & 0x7F) {
		case MessageSync:
			ply = ntohs(ncq->Data.X) << 16;
			ply |= ntohs(ncq->Data.Y);
			if (ply != NetworkSyncSeeds[GameCycle & 0xFF] ||
					ntohs(ncq->Data.Unit) != NetworkSyncHashs[GameCycle & 0xFF]) {

				SetMessage(_("Network out of sync"));
				DebugPrint("\nNetwork out of sync %x!=%x! %d!=%d! Cycle %lu\n\n" _C_
					ply _C_ NetworkSyncSeeds[GameCycle & 0xFF] _C_
					ntohs(ncq->Data.Unit) _C_ NetworkSyncHashs[GameCycle & 0xFF] _C_ GameCycle);
			}
			return;
		case MessageChat:
		case MessageChatTerm: {
			const NetworkChat *ncm;

			ncm = (NetworkChat *)(&ncq->Data);
			ply = ncm->Player;
			if (NetMsgBufLen[ply] + sizeof(ncm->Text) < 128) {
				memcpy(((char *)NetMsgBuf[ply]) + NetMsgBufLen[ply], ncm->Text,
						sizeof(ncm->Text));
			}
			NetMsgBufLen[ply] += sizeof(ncm->Text);
			if (ncq->Type == MessageChatTerm) {
				NetMsgBuf[ply][127] = '\0';
				SetMessage("%s", NetMsgBuf[ply]);
				NetMsgBufLen[ply] = 0;
			}
			}
			break;
		case MessageQuit:
			NetworkRemovePlayer(ncq->Data.X);
			CommandLog("quit", NoUnitP, FlushCommands, ncq->Data.X, -1, NoUnitP, NULL, -1);
			CommandQuit(ncq->Data.X);
			break;
		case MessageExtendedCommand: {
			const NetworkExtendedCommand *nec;

			nec = (NetworkExtendedCommand *)(&ncq->Data);
			ParseExtendedCommand(nec->ExtendedType, (ncq->Type & 0x80) >> 7,
				nec->Arg1, ntohs(nec->Arg2), ntohs(nec->Arg3), ntohs(nec->Arg4));
			}
			break;
		case MessageNone:
			// Nothing to Do, This Message Should Never be Executed
			Assert(0);
			break;
		default:
			ParseCommand(ncq->Type, ntohs(ncq->Data.Unit),
				ntohs(ncq->Data.X), ntohs(ncq->Data.Y), ntohs(ncq->Data.Dest));
			break;
	}
}

/**
**  Network resend commands, we have a missing packet send to all clients
**  what packet we are missing.
**
**  @todo
**  We need only send to the clients, that have not delivered the packet.
*/
static void NetworkResendCommands(void)
{
	NetworkPacket packet;

#ifdef DEBUG
	++NetworkSendResend;
#endif

	//
	// Build packet
	//
	memset(&packet, 0, sizeof(packet));
	packet.Header.Type[0] = MessageResend;
	packet.Header.Type[1] = MessageNone;
	packet.Header.Cycle =
		(GameCycle / NetworkUpdates) * NetworkUpdates + NetworkUpdates;

	// if (0 || !(rand() & 15))
	NetworkBroadcast(&packet, sizeof(NetworkPacketHeader) + sizeof(NetworkCommand));
}

/**
**  Network send commands.
*/
static void NetworkSendCommands(void)
{
	NetworkCommandQueue *incommand;
	NetworkCommandQueue *ncq;
	int numcommands;

	//
	// No command available, send sync.
	//
	numcommands = 0;
	incommand = NULL;
	ncq = NetworkIn[(GameCycle + NetworkLag) & 0xFF][ThisPlayer->Index];
	memset(ncq, 0, sizeof(NetworkCommandQueue) * MaxNetworkCommands);
	if (CommandsIn.empty() && MsgCommandsIn.empty()) {
		ncq[0].Type = MessageSync;
		ncq[0].Data.Unit = htons(SyncHash&0xFFFF);
		ncq[0].Data.X = htons(SyncRandSeed>>16);
		ncq[0].Data.Y = htons(SyncRandSeed&0xFFFF);
		ncq[0].Time = GameCycle + NetworkLag;
		numcommands = 1;
	} else {
		while ((!CommandsIn.empty() || !MsgCommandsIn.empty()) &&
				numcommands < MaxNetworkCommands) {
			if (!CommandsIn.empty()) {
				incommand = CommandsIn.front();
#ifdef DEBUG
				if (incommand->Type != MessageExtendedCommand) {
					// FIXME: we can send destoyed units over network :(
					if (UnitSlots[ntohs(ncq->Data.Unit)]->Destroyed) {
						DebugPrint("Sending destroyed unit %d over network!!!!!!\n" _C_
							ntohs(incommand->Data.Unit));
					}
				}
#endif
				CommandsIn.pop_front();
			} else {
				incommand = MsgCommandsIn.front();
				MsgCommandsIn.pop_front();
			}
			memcpy(&ncq[numcommands], incommand, sizeof(NetworkCommandQueue));
			ncq[numcommands].Time = GameCycle + NetworkLag;
			++numcommands;
			FreeNCQ(incommand);
		}
	}

	if (numcommands != MaxNetworkCommands) {
		ncq[numcommands].Type = MessageNone;
	}

	NetworkSendPacket(ncq);

	NetworkSyncSeeds[(GameCycle + NetworkLag) & 0xFF] = SyncRandSeed;
	NetworkSyncHashs[(GameCycle + NetworkLag) & 0xFF] = SyncHash & 0xFFFF; // FIXME: 32bit later
}

/**
**  Network excecute commands.
*/
static void NetworkExecCommands(void)
{
	NetworkCommandQueue *ncq;

	//
	// Must execute commands on all computers in the same order.
	//
	for (int i = 0; i < NumPlayers; ++i) {
		//
		// Remove commands.
		//
		for (int c = 0; c < MaxNetworkCommands; ++c) {
			ncq = &NetworkIn[GameCycle & 0xFF][i][c];
			if (ncq->Type == MessageNone) {
				break;
			}
			if (ncq->Time) {
#ifdef DEBUG
				if (ncq->Time != GameCycle) {
					DebugPrint("cycle %lu idx %lu time %lu\n" _C_
						GameCycle _C_ GameCycle & 0xFF _C_ ncq->Time);
					Assert(ncq->Time == GameCycle);
				}
#endif
				ParseNetworkCommand(ncq);
			}
		}
	}
}

/**
**  Network synchronize commands.
*/
static void NetworkSyncCommands(void)
{
	const NetworkCommandQueue *ncq;
	unsigned long n;

	//
	// Check if all next messages are available.
	//
	NetworkInSync = 1;
	n = GameCycle + NetworkUpdates;
	for (int i = 0; i < HostsCount; ++i) {
		ncq = NetworkIn[n & 0xFF][Hosts[i].PlyNr];
		if (ncq[0].Time != n) {
			NetworkInSync = 0;
			NetworkDelay = FrameCounter + NetworkUpdates;
			// FIXME: should send a resend request.
			break;
		}
	}
}

/**
**  Handle network commands.
*/
void NetworkCommands(void)
{
	if (IsNetworkGame()) {
		if (!(GameCycle % NetworkUpdates)) {
			// Send messages to all clients (other players)
			NetworkSendCommands();
			NetworkExecCommands();
			NetworkSyncCommands();
		}
	}
}


/**
**  Recover network.
*/
void NetworkRecover(void)
{
	if (HostsCount == 0) {
		NetworkInSync = 1;
		return;
	}

	if (FrameCounter > NetworkDelay) {
		NetworkDelay += NetworkUpdates;

		// Check for players that timed out
		for (int i = 0; i < HostsCount; ++i) {
			int secs;

			if (!NetworkLastFrame[Hosts[i].PlyNr]) {
				continue;
			}

			secs = (FrameCounter - NetworkLastFrame[Hosts[i].PlyNr]) /
				(FRAMES_PER_SECOND * VideoSyncSpeed / 100);
			// FIXME: display a menu while we wait
			if (secs >= 3 && secs < NetworkTimeout) {
				if (FrameCounter % FRAMES_PER_SECOND < (unsigned long)NetworkUpdates) {
					SetMessage(_("Waiting for player \"%s\": %d:%02d"), Hosts[i].PlyName,
						(NetworkTimeout - secs) / 60, (NetworkTimeout - secs) % 60);
				}
			}
			if (secs >= NetworkTimeout) {
				NetworkCommand nc;
				const NetworkCommandQueue *ncq;
				unsigned long n;
				NetworkPacket np;

				n = GameCycle + NetworkUpdates;
				nc.X = Hosts[i].PlyNr;
				NetworkIn[n & 0xFF][Hosts[i].PlyNr][0].Time = n;
				NetworkIn[n & 0xFF][Hosts[i].PlyNr][0].Type = MessageQuit;
				NetworkIn[n & 0xFF][Hosts[i].PlyNr][0].Data = nc;
				PlayerQuit[Hosts[i].PlyNr] = 1;
				SetMessage(_("Timed out"));

				ncq = &NetworkIn[n & 0xFF][Hosts[i].PlyNr][0];
				np.Header.Cycle = ncq->Time & 0xFF;
				np.Header.Type[0] = ncq->Type;
				np.Header.Type[1] = MessageNone;

				NetworkBroadcast(&np, sizeof(NetworkPacketHeader) + sizeof(NetworkCommand));

				NetworkSyncCommands();
			}
		}

		// Resend old commands
		NetworkResendCommands();
	}
}

//@}
