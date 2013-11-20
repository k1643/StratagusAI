
#ifndef __SOCKET_H__
#define __SOCKET_H__


void SocketInterfaceInit();
void DoSocketInterface();

extern int SocketInterfacePort;
extern int CyclesPerVideoUpdate;
extern unsigned GameCyclesPerTransition;
extern unsigned long LastPausedCycle;
extern char WarpSpeed;



#endif   // __SOCKET_H__
