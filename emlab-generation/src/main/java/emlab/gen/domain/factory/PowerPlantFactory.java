package emlab.gen.domain.factory;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.transaction.annotation.Transactional;

import emlab.gen.domain.agent.EnergyProducer;
import emlab.gen.domain.contract.Loan;
import emlab.gen.domain.gis.Zone;
import emlab.gen.domain.market.electricity.ElectricitySpotMarket;
import emlab.gen.domain.technology.PowerGeneratingTechnology;
import emlab.gen.domain.technology.PowerGridNode;
import emlab.gen.domain.technology.PowerPlant;

public class PowerPlantFactory implements InitializingBean {

	private Set<PowerGridNode> nodes;

	private Set<PowerPlant> plants;

	private ElectricitySpotMarket market;

	private List<EnergyProducer> producers;

	static final Logger logger = LoggerFactory
			.getLogger(PowerPlantFactory.class);

	@Override
	public void afterPropertiesSet() throws Exception {
		createPowerPlantsForMarket(market);
	}

	@Transactional
	private void setPowerPlantProperties(PowerPlant plant,
			EnergyProducer energyProducer, PowerGridNode location,
			PowerGeneratingTechnology technology) {
		plant.setOwner(energyProducer);
		plant.setLocation(location);
		plant.setConstructionStartTime(-(technology.getExpectedLeadtime()
				+ technology.getExpectedPermittime() + 2013 - plant
				.getYearFirstOperational()));
		plant.setActualLeadtime(plant.getTechnology().getExpectedLeadtime());
		plant.setActualPermittime(plant.getTechnology().getExpectedPermittime());
		plant.setExpectedEndOfLife(plant.getConstructionStartTime()
				+ plant.getActualPermittime() + plant.getActualLeadtime()
				+ plant.getTechnology().getExpectedLifetime());
		plant.calculateAndSetActualInvestedCapital(plant
				.getConstructionStartTime());
		plant.calculateAndSetActualEfficiency(plant.getConstructionStartTime());
		plant.setActualNominalCapacity(technology.getCapacity()
				* plant.getGenerators());
		plant.setDismantleTime(1000);
		Loan loan = new Loan().persist();
		loan.setFrom(energyProducer);
		loan.setTo(null);
		double amountPerPayment = determineLoanAnnuities(
				plant.getActualInvestedCapital()
						* energyProducer.getDebtRatioOfInvestments(), plant
						.getTechnology().getDepreciationTime(),
				energyProducer.getLoanInterestRate());
		loan.setAmountPerPayment(amountPerPayment);
		loan.setTotalNumberOfPayments(plant.getTechnology()
				.getDepreciationTime());
		loan.setLoanStartTime(plant.getConstructionStartTime());
		loan.setNumberOfPaymentsDone(-plant.getConstructionStartTime());
		plant.setLoan(loan);
	}

	private void createPowerPlantsForMarket(ElectricitySpotMarket market) {

		for (PowerPlant plant : plants) {
			PowerGeneratingTechnology technology = plant.getTechnology();
			EnergyProducer energyProducer = getRandomProducer(producers);
			setPowerPlantProperties(plant, energyProducer,
					getNodeForZone(market.getZone()), technology);
		}
	}

	private EnergyProducer getRandomProducer(List<EnergyProducer> producers) {
		if (producers.size() > 0) {
			int size = producers.size();
			int index = getRandomIndexFromList(size);
			return producers.get(index);
		}
		return null;
	}

	private int getRandomIndexFromList(int size) {
		return (int) Math.min(Math.floor(Math.random() * size), size - 1);
	}

	private PowerGridNode getNodeForZone(Zone zone) {
		for (PowerGridNode node : nodes) {
			if (node.getZone().equals(zone)) {
				return node;
			}
		}
		return null;
	}

	public Set<PowerGridNode> getNodes() {
		return nodes;
	}

	public void setNodes(Set<PowerGridNode> nodes) {
		this.nodes = nodes;
	}

	public Set<PowerPlant> getPlants() {
		return plants;
	}

	public void setPlants(Set<PowerPlant> plants) {
		this.plants = plants;
	}

	public ElectricitySpotMarket getMarket() {
		return market;
	}

	public void setMarket(ElectricitySpotMarket market) {
		logger.info("setting market {}", market);
		this.market = market;
	}

	public List<EnergyProducer> getProducers() {
		return producers;
	}

	public void setProducers(List<EnergyProducer> producers) {
		this.producers = producers;
	}

	public double determineLoanAnnuities(double totalLoan, double payBackTime,
			double interestRate) {

		double q = 1 + interestRate;
		double annuity = totalLoan * (Math.pow(q, payBackTime) * (q - 1))
				/ (Math.pow(q, payBackTime) - 1);

		return annuity;
	}

}
