
SetPlayerData(GetThisPlayer(), "RaceName", "orc")

DefaultObjectives = {"-Destroy the enemy"}
Objectives = DefaultObjectives

function InitGameSettings()
  GameSettings.NetGameType = 1
  for i=0,PlayerMax-1 do
    GameSettings.Presets[i].Race = -1
    GameSettings.Presets[i].Team = -1
    GameSettings.Presets[i].Type = -1
  end
  GameSettings.Resources = -1
  GameSettings.NumUnits = -1
  GameSettings.Opponents = -1
  GameSettings.Terrain = -1
  GameSettings.GameType = -1
  GameSettings.NoFogOfWar = false
  GameSettings.RevealMap = 0
end

InitGameSettings()

function RunMap(map, objective, fow, revealmap)
  if objective == nil then
    Objectives = DefaultObjectives
  else
    Objectives = objective
  end
  loop = true
  while (loop) do
    InitGameVariables()
    SetFogOfWar(false)
    RevealMap()
    StartMap(map)
    -- if GameResult ~= GameRestart then
    --   loop = false
    -- end
  end
  
  -- TODO: notify client of state

  InitGameSettings()
  SetPlayerData(GetThisPlayer(), "RaceName", "orc")
end

mapname = "maps/default.smp"
local mapinfo = {
  playertypes = {nil, nil, nil, nil, nil, nil, nil, nil},
  description = "",
  nplayers = 1,
  w = 32,
  h = 32,
  id = 0
}

function GetMapInfo(mapname)
  local OldDefinePlayerTypes = DefinePlayerTypes
  local OldPresentMap = PresentMap

  function DefinePlayerTypes(p1, p2, p3, p4, p5, p6, p7, p8)
    mapinfo.playertypes[1] = p1
    mapinfo.playertypes[2] = p2
    mapinfo.playertypes[3] = p3
    mapinfo.playertypes[4] = p4
    mapinfo.playertypes[5] = p5
    mapinfo.playertypes[6] = p6
    mapinfo.playertypes[7] = p7
    mapinfo.playertypes[8] = p8

    mapinfo.nplayers = 0
    for i=0,8 do
      local t = mapinfo.playertypes[i]
      if (t == "person" or t == "computer") then
        mapinfo.nplayers = mapinfo.nplayers + 1
      end
    end
  end

  function PresentMap(description, nplayers, w, h, id)
    mapinfo.description = description
    -- nplayers includes rescue-passive and rescue-active
    -- calculate the real nplayers in DefinePlayerTypes
    --mapinfo.nplayers = nplayers
    mapinfo.w = w
    mapinfo.h = h
    mapinfo.id = id
  end

  Load(mapname)

  DefinePlayerTypes = OldDefinePlayerTypes
  PresentMap = OldPresentMap
end

LoadGameFile = nil

Load("data/scripts/menus/campaign.lua")
Load("data/scripts/menus/load.lua")
Load("data/scripts/menus/save.lua")
Load("data/scripts/menus/replay.lua")
Load("data/scripts/menus/options.lua")
Load("data/scripts/menus/credits.lua")
Load("data/scripts/menus/game.lua")
Load("data/scripts/menus/help.lua")
Load("data/scripts/menus/objectives.lua")
Load("data/scripts/menus/endscenario.lua")
Load("data/scripts/menus/diplomacy.lua")
Load("data/scripts/menus/results.lua")
Load("data/scripts/menus/network.lua")


-- Game loop
-- 
while(true) do
	mapname = "maps/default.smp"
	RunMap(mapname)
end

