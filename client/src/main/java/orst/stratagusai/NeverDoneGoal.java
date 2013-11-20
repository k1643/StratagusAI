package orst.stratagusai;

import org.apache.log4j.Logger;

/**
 * The goal class tests when an episode is done.  This is used in the
 * game loop to tell when to end an episode.
 *
 * @author bking
 */
public class NeverDoneGoal extends Goal
{
	private static final Logger log = Logger.getLogger(NeverDoneGoal.class);

	@Override
	public boolean episodeDone(GameProxy game)
	{
		log.debug("never done.");
		return false;
	}
}
