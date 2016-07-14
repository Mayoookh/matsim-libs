package playground.pieter.network;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.Link;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class NetworkMinimumCapacityChanger {

	void run(final String[] args) {
		Scenario scenario;
		MatsimRandom.reset(123);
		scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(args[0]);
		ArrayList<String> linkChangeList = new ArrayList<>();
		int carLinkCount = 0;
		double originalCapacity = 0d;
		double newCapacity = 0d;

		for (Link l : scenario.getNetwork().getLinks().values()) {
			if (l.getAllowedModes().contains("car")) {
				carLinkCount++;
				originalCapacity += l.getCapacity();
				if ((l.getCapacity() / l.getNumberOfLanes()) < Double
						.parseDouble(args[1])) {
					linkChangeList.add(l.getId().toString());
				} else {
					newCapacity += l.getCapacity();

				}
			}

		}

		for (String id : linkChangeList) {
			Link l = scenario.getNetwork().getLinks().get(Id.createLinkId(id));
			l.setCapacity(Double.parseDouble(args[1]) * l.getNumberOfLanes());
			newCapacity += l.getCapacity();
		}
		System.out
				.println(String
						.format("Changed %d out of %d car links to capacity %s per lane.\n(network total across all modes: %d)",
								linkChangeList.size(), carLinkCount, args[1],
								scenario.getNetwork().getLinks().size()));
		System.out
				.println(String
						.format("That is an overall increase in car capacity from %f to %f vehicles per hour",
								originalCapacity, newCapacity));
		new NetworkWriter(scenario.getNetwork()).write(args[2]);
	}

	/**
	 * @param args
	 *            - An array of String, Double, String:
	 *            <ol>
	 *            <li>The name of the network;</li>
	 *            <li>the minimum flow capacity per lane;</li>
	 *            <li>and the name of the output network.</li>
	 *            </ol>
	 */
	public static void main(final String[] args) {
		new NetworkMinimumCapacityChanger().run(args);
	}

}
