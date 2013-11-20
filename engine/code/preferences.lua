
-- the preferences.lua script is loaded by stratagus.lua.
--
-- To turn off fog of war we need to call RevealMap() and SetFogOfWar(false).
-- the value of preferences.FogOfWar will be passed to SetFogOfWar()
-- in the default configuration script stratagus.lua
--
RevealMap()

preferences = {
    VideoWidth = 800,
    VideoHeight = 600,
    VideoFullScreen = false,
    PlayerName = "Player",
    FogOfWar = false,
    ShowCommandKey = true,
    GroupKeys = "0123456789`",
    GameSpeed = 30,
    EffectsEnabled = false,
    EffectsVolume = 0,
    MusicEnabled = false,
    MusicVolume = 0,
    StratagusTranslation = "",
    GameTranslation = "",
    TipNumber = 0,
    ShowTips = false,
    GrabMouse = false,
  }

-- replace end of game trigger so that game can be restarted by client.
--
function SinglePlayerTriggers()
  AddTrigger(
    function() return GetPlayerData(GetThisPlayer(), "TotalNumUnits") == -1 end,
    function() return ActionDefeat() end)

  AddTrigger(
    function() return GetNumOpponents(GetThisPlayer()) == -1 end,
    function() return ActionVictory() end)
end