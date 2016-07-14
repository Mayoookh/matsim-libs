package usecases.chessboard;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.freight.carrier.*;
import org.matsim.contrib.freight.jsprit.VehicleTypeDependentRoadPricingCalculator;
import org.matsim.contrib.freight.scoring.CarrierScoringFunctionFactory;
import org.matsim.contrib.freight.scoring.FreightActivity;
import org.matsim.core.network.Link;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.vehicles.Vehicle;

import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines example carrier scoring function (factory).
 *
 * <p>Just saw that there are some Deprecations. Needs to be adapted.
 *
 * @author stefan
 *
 */
public class CarrierScoringFunctionFactoryImpl implements CarrierScoringFunctionFactory {

    /**
     *
     * Example activity scoring that penalizes missed time-windows with 1.0 per second.
     *
     * @author stefan
     *
     */
    public static class DriversActivityScoring implements SumScoringFunction.BasicScoring, SumScoringFunction.ActivityScoring {

        private static Logger log = Logger.getLogger(DriversActivityScoring.class);

        private double score;

        private double timeParameter = 0.008;

        private double missedTimeWindowPenalty = 0.01;

        private FileWriter fileWriter;

        public DriversActivityScoring() {
            super();
//			try {
//				fileWriter = new FileWriter(new File("output/act_scoring_"+System.currentTimeMillis()+".txt"));
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
        }

        @Override
        public void finish() {
//			try {
//				fileWriter.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
        }

        @Override
        public double getScore() {
            return score;
        }

        @Override
        public void handleFirstActivity(Activity act) {
            handleActivity(act);
        }

        @Override
        public void handleActivity(Activity act) {
            if(act instanceof FreightActivity) {
                double actStartTime = act.getStartTime();

//                log.info(act + " start: " + Time.writeTime(actStartTime));
                TimeWindow tw = ((FreightActivity) act).getTimeWindow();
                if(actStartTime > tw.getEnd()){
                    double penalty_score = (-1)*(actStartTime - tw.getEnd())*missedTimeWindowPenalty;
                    assert penalty_score <= 0.0 : "penalty score must be negative";
//                    log.info("penalty " + penalty_score);
                    score += penalty_score;

                }
                double actTimeCosts = (act.getEndTime()-actStartTime)*timeParameter;
//                log.info("actCosts " + actTimeCosts);
                assert actTimeCosts >= 0.0 : "actTimeCosts must be positive";
                score += actTimeCosts*(-1);
//                try {
//					fileWriter.write("actLinkId="+ act.getLinkId() + "; actArrTime=" + Time.writeTime(actStartTime) +
//							"; twEnd=" + tw.getEnd() + "; minTooLate=" + Time.writeTime(Math.max(0, actStartTime-tw.getEnd()))
//							+ "; penaltyMissedTW=" + (Math.max(0, actStartTime-tw.getEnd())*missedTimeWindowPenalty) +
//							"; actCosts=" +actTimeCosts + "\n");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
            }
        }

        @Override
        public void handleLastActivity(Activity act) {
            handleActivity(act);
        }

    }

    public static class VehicleEmploymentScoring implements SumScoringFunction.BasicScoring {

        private Carrier carrier;

        private FileWriter fileWriter;

        public VehicleEmploymentScoring(Carrier carrier) {
            super();
            this.carrier = carrier;
//			try {
//				fileWriter = new FileWriter(new File("output/veh_employment_"+System.currentTimeMillis()+".txt"));
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
        }

        @Override
        public void finish() {

        }

        @Override
        public double getScore() {
            double score = 0.;
            CarrierPlan selectedPlan = carrier.getSelectedPlan();
            if(selectedPlan == null) return 0.;
            for(ScheduledTour tour : selectedPlan.getScheduledTours()){
                if(!tour.getTour().getTourElements().isEmpty()){
                    score += (-1)*tour.getVehicle().getVehicleType().getVehicleCostInformation().fix;
//					try {
//						fileWriter.write("vehicleId="+tour.getVehicle().getVehicleId()+"; fix="+tour.getVehicle().getVehicleType().getVehicleCostInformation().fix+"\n");
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
                }
            }
//			try {
//				fileWriter.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
            return score;
        }

    }

    /**
     * Example leg scoring.
     *
     * @author stefan
     *
     */
    public static class DriversLegScoring implements SumScoringFunction.BasicScoring, SumScoringFunction.LegScoring {

        private double score = 0.0;

        private final Network network;

        private final Carrier carrier;

        private Set<CarrierVehicle> employedVehicles;

        public DriversLegScoring(Carrier carrier, Network network) {
            super();
            this.network = network;
            this.carrier = carrier;
            employedVehicles = new HashSet<CarrierVehicle>();
        }


        @Override
        public void finish() {

        }


        @Override
        public double getScore() {
            return score;
        }

        private double getTimeParameter(CarrierVehicle vehicle) {
            return vehicle.getVehicleType().getVehicleCostInformation().perTimeUnit;
        }


        private double getDistanceParameter(CarrierVehicle vehicle) {
            return vehicle.getVehicleType().getVehicleCostInformation().perDistanceUnit;
        }


        private CarrierVehicle getVehicle(Id vehicleId) {
            for(CarrierVehicle cv : carrier.getCarrierCapabilities().getCarrierVehicles()){
                if(cv.getVehicleId().equals(vehicleId)){
                    return cv;
                }
            }
            return null;
        }

        @Override
        public void handleLeg(Leg leg) {
            if(leg.getRoute() instanceof NetworkRoute){
                NetworkRoute nRoute = (NetworkRoute) leg.getRoute();
                Id vehicleId = nRoute.getVehicleId();
                CarrierVehicle vehicle = getVehicle(vehicleId);
                if(vehicle == null) throw new IllegalStateException("vehicle with id " + vehicleId + " is missing");
                if(!employedVehicles.contains(vehicle)){
                    employedVehicles.add(vehicle);
                }
                double distance = 0.0;
                double toll = 0.;
                if(leg.getRoute() instanceof NetworkRoute){
                    Link startLink = network.getLinks().get(leg.getRoute().getStartLinkId());
                    distance += startLink.getLength();
                    for(Id linkId : ((NetworkRoute) leg.getRoute()).getLinkIds()){
                        distance += network.getLinks().get(linkId).getLength();

                    }
                    distance += network.getLinks().get(leg.getRoute().getEndLinkId()).getLength();

                }

                double distanceCosts = distance*getDistanceParameter(vehicle);
                assert distanceCosts >= 0.0 : "distanceCosts must be positive";
                score += (-1) * distanceCosts;
                double timeCosts = leg.getTravelTime()*getTimeParameter(vehicle);
                assert timeCosts >= 0.0 : "timeCosts must be positive";
                score += (-1) * timeCosts;

            }
        }

    }


    public static class TollScoring implements SumScoringFunction.BasicScoring, SumScoringFunction.ArbitraryEventScoring {

        private double score = 0.;

        private Carrier carrier;

        private Network network;

        private VehicleTypeDependentRoadPricingCalculator roadPricing;

        public TollScoring(Carrier carrier, Network network, VehicleTypeDependentRoadPricingCalculator roadPricing) {
            this.carrier = carrier;
            this.roadPricing = roadPricing;
            this.network = network;
        }

        @Override
        public void handleEvent(Event event) {
            if(event instanceof LinkEnterEvent){
                CarrierVehicle carrierVehicle = getVehicle(((LinkEnterEvent) event).getVehicleId());
                if(carrierVehicle == null) throw new IllegalStateException("carrier vehicle missing");
                double toll = roadPricing.getTollAmount(carrierVehicle.getVehicleType().getId(),network.getLinks().get(((LinkEnterEvent) event).getLinkId()),event.getTime());
                if(toll > 0.) System.out.println("bing: vehicle " + carrierVehicle.getVehicleId() + " paid toll " + toll + "");
                score += (-1) * toll;
            }
        }

        private CarrierVehicle getVehicle(Id<Vehicle> vehicleId) {
            for(CarrierVehicle v : carrier.getCarrierCapabilities().getCarrierVehicles()){
                if(v.getVehicleId().equals(vehicleId)){
                    return v;
                }
            }
            return null;
        }

        @Override
        public void finish() {

        }

        @Override
        public double getScore() {
            return score;
        }
    }

    private Network network;

    public CarrierScoringFunctionFactoryImpl(Network network) {
        super();
        this.network = network;
    }


    @Override
    public ScoringFunction createScoringFunction(Carrier carrier) {
        SumScoringFunction sf = new SumScoringFunction();
        DriversLegScoring driverLegScoring = new DriversLegScoring(carrier, network);
        VehicleEmploymentScoring vehicleEmployment = new VehicleEmploymentScoring(carrier);
//		DriversActivityScoring actScoring = new DriversActivityScoring();
        sf.addScoringFunction(driverLegScoring);
        sf.addScoringFunction(vehicleEmployment);
//		sf.addScoringFunction(actScoring);
        return sf;
    }



}
