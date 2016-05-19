package ca.ubc.ctlt.copyalerts.db.operations;

import blackboard.persist.PersistenceException;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

import java.util.Map;

public abstract class ScanProcessor
{
	/**
	 * @param result Key is the name of the data columns we're interested in.
	 * @throws PersistenceException
	 */
	abstract public void scan(Map<String, String> result) throws PersistenceException;
	/**
	 * Since processors work on batch sizes, there might be stuff remaining
	 * in the batch that hasn't been processed, so we need a way to trigger
	 * processing for the unfinished batch.
	 *
	 * @param wasInterrupted - Indicates whether the cleanup was triggered by an
	 * interruption or we actually run to completion
	 */
	abstract public void cleanup(boolean wasInterrupted);
}
