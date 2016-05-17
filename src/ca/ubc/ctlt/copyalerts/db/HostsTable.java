package ca.ubc.ctlt.copyalerts.db;

import blackboard.persist.PersistenceRuntimeException;
import blackboard.persist.dao.impl.SimpleDAO;
import blackboard.persist.impl.SimpleSelectQuery;
import blackboard.persist.impl.UpdateQuery;
import blackboard.persist.impl.mapping.DbObjectMap;
import blackboard.persist.impl.mapping.annotation.AnnotationMappingFactory;
import blackboard.platform.query.Criteria;
import ca.ubc.ctlt.copyalerts.db.entities.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class HostsTable extends SimpleDAO<Host>
{
	private static final DbObjectMap HOST_EXT_MAP = AnnotationMappingFactory.getMap(Host.class);

	private final static Logger logger = LoggerFactory.getLogger(HostsTable.class);

	// keeps track of which nodes are running a copy of this building block so we can determine
	// which node should run the file indexing job
	private HashMap<String, Boolean> hosts = new HashMap<>();
	
	public HostsTable() throws PersistenceRuntimeException
	{
		super(HOST_EXT_MAP);
		loadHosts();
	}

	/**
	 * Add a new host.
	 * 
	 * @param host   host to add
	 * @param leader if the host is leader
	 * @throws PersistenceRuntimeException
     */
	private void addHost(String host, boolean leader) throws PersistenceRuntimeException
	{
		if (hosts.containsKey(host))
		{ // entry already exists
			return;
		}

		this.persist(new Host(host, leader));
		hosts.put(host, leader);
		logger.info("Added " + host + " to host list");
	}

	/**
	 * Defaults to adding a non-leader host 
	 * @param host String host to add
	 */
	public void addHost(String host)
	{
		addHost(host, false);
	}
	
	/**
	 * Delete the entry in the database corresponding to the given host.
	 * @param host String host to delete
	 * @throws PersistenceRuntimeException
	 */
	public void deleteHost(String host) throws PersistenceRuntimeException
	{
	    Host hostObj = getHostByName(host);
		if (hostObj != null) {
			this.deleteById(hostObj.getId());
		}
		hosts.remove(host);
		logger.info("Host " + host + " removed");
	}
	
	/**
	 * Load existing hosts from the database.
	 * 
	 * @throws PersistenceRuntimeException
	 */
	public void loadHosts() throws PersistenceRuntimeException
	{
		List<Host> hostList = this.loadAll();
		hosts.clear();

		for(Host host: hostList) {
			hosts.put(host.getHost(), host.isLeader());
		}
	}

	private Host getHostByName(String host) {
		SimpleSelectQuery query = new SimpleSelectQuery(this.getDAOSupport().getMap());

		Criteria criteria = query.getCriteria();

		criteria.add(criteria.equal("host", host));

		List<Host> hostList = getDAOSupport().loadList(query);

		if (hostList.size() == 0) {
			return null;
		}

		return hostList.get(0);
	}
	/**
	 * Get the current leader that is supposed to be executing the indexing job.
	 * @return The current leader of the round if there is one, empty otherwise
	 */
	public String getLeader()
	{
		for (Entry<String, Boolean> e : hosts.entrySet())
		{
			if (e.getValue())
			{
				return e.getKey();
			}
		}
		return "";
	}

	public boolean isLeader(String hostname) {
		return getLeader().equals(hostname);
	}

	public Host getLeaderHost() {
		return getHostByName(getLeader());
	}
	
	/**
	 * Set the current leader to the given host.
	 * @param host String identifying the hostname that is supposed to be executing then indexing job.
	 * @throws PersistenceRuntimeException
	 */
	public void setLeader(String host) throws PersistenceRuntimeException
	{
		if (!hosts.containsKey(host) || hosts.get(host))
		{ // do nothing if we don't have the host entry or if the host is already the leader
			return;
		}
		String formerLeader = getLeader();

		// demote the current leader
		// promote the new leader
		SimpleSelectQuery query = new SimpleSelectQuery(this.getDAOSupport().getMap());

		Criteria criteria = query.getCriteria();

		criteria.add(criteria.or(
				criteria.equal("host", host),
				criteria.equal("host", formerLeader)
		));

		List<Host> hostList = getDAOSupport().loadList(query);

		for(Host hostObj: hostList) {
			UpdateQuery updateQuery = new UpdateQuery(this.getDAOSupport().getMap(), hostObj);
			if (host.equals(hostObj.getHost())) {
				hostObj.setLeader(true);
			} else {
				hostObj.setLeader(false);
			}
			getDAOSupport().execute(updateQuery);
		}


		// update in memory mapping
		hosts.put(formerLeader, false);
		hosts.put(host, true);
	}
	
	/**
	 * Returns true if host already exists in the database, false otherwise.
	 * @param host
	 * @return
	 */
	public boolean hasHost(String host)
	{
		return hosts.containsKey(host);
	}

	/**
	 * @return the hosts
	 */
	public HashMap<String, Boolean> getHosts()
	{
		return hosts;
	}
}
