package housing;

import java.util.Iterator;

import org.apache.commons.math3.random.MersenneTwister;
import utilities.PriorityQueue2D;

/*******************************************************
 * Class that represents market for houses for-sale.
 * 
 * @author daniel, Adrian Carro
 *
 *****************************************************/
public class HouseSaleMarket extends HousingMarket {
	private static final long serialVersionUID = -2878118108039744432L;

	private Config                                  config = Model.config; // Passes the Model's configuration parameters object to a private field
    private PriorityQueue2D<HousingMarketRecord>    offersPY;

	public HouseSaleMarket(MersenneTwister prng) {
		super(prng);  // previously I think you were missing this call to HousingMarket constructor!
		offersPY = new PriorityQueue2D<>(new HousingMarketRecord.PYComparator());
	}
	
	@Override
	public void init() {
		super.init();
		offersPY.clear();
	}
		
	/**
	 * This method deals with doing all the stuff necessary whenever a house gets sold.
	 */
	public void completeTransaction(HouseBuyerRecord purchase, HouseSaleRecord sale) {
        // TODO: Revise if it makes sense to have recordTransaction as a separate method from recordSale
		Model.housingMarketStats.recordTransaction(sale);
		sale.house.saleRecord = null;
		Household buyer = purchase.buyer;
		if(buyer == sale.house.owner) return; // TODO: Shouldn't this if be the first line in this method?
		sale.house.owner.completeHouseSale(sale);
		buyer.completeHousePurchase(sale);
        Model.housingMarketStats.recordSale(purchase, sale);
		sale.house.owner = buyer;
	}

	@Override
	public HouseSaleRecord offer(House house, double price) {
		HouseSaleRecord hsr = super.offer(house, price);
		offersPY.add(hsr);
		house.putForSale(hsr);
		return(hsr);
	}
	
	@Override
	public void removeOffer(HouseSaleRecord hsr) {
		super.removeOffer(hsr);
		offersPY.remove(hsr);
		hsr.house.resetSaleRecord();
	}
	
	@Override
	public void updateOffer(HouseSaleRecord hsr, double newPrice) {
		offersPY.remove(hsr);
		super.updateOffer(hsr, newPrice);
		offersPY.add(hsr);
	}

    /**
     * This method overrides the main simulation step in order to sort the price-yield priorities.
     */
    @Override
    void clearMarket() {
        // Before any use, priorities must be sorted by filling in the uncoveredElements TreeSet at the corresponding
        // PriorityQueue2D. In particular, we sort here the price-yield priorities
        offersPY.sortPriorities();
        // Then continue with the normal HousingMarket clearMarket mechanism
        super.clearMarket();
    }
	
	@Override
	protected HouseSaleRecord getBestOffer(HouseBuyerRecord bid) {
		if(bid.getClass() == BTLBuyerRecord.class) { // BTL buyer (yield driven)
			HouseSaleRecord bestOffer = (HouseSaleRecord)offersPY.peek(bid);
			if(bestOffer != null) {
					double minDownpayment = bestOffer.getPrice()*(1.0
                            - Model.rentalMarketStats.getExpAvFlowYield()/
                            (Model.centralBank.getInterestCoverRatioLimit(false)*config.CENTRAL_BANK_BTL_STRESSED_INTEREST));
					if(bid.buyer.getBankBalance() >= minDownpayment) {
						return(bestOffer);
					}
			}
			return(null);
		} else { // must be OO buyer (quality driven)
			return super.getBestOffer(bid);
		}
	}

    /**
     * Overrides corresponding method at HousingMarket in order to remove successfully matched and cleared offers from
     * the offersPY queue
     *
     * @param record Iterator over the HousingMarketRecord objects contained in offersPQ
     * @param offer Offer to remove from queues
     */
	@Override
    void removeOfferFromQueues(Iterator<HousingMarketRecord> record, HouseSaleRecord offer) {
        record.remove();
        offersPY.remove(offer);
    }

	/*******************************************
	 * Make a bid on the market as a Buy-to-let investor
	 *  (i.e. make an offer on a (yet to be decided) house).
	 * 
	 * @param buyer The household that is making the bid.
	 * @param maxPrice The maximum price that the household is willing to pay.
	 ******************************************/
	void BTLbid(Household buyer, double maxPrice) { bids.add(new BTLBuyerRecord(buyer, maxPrice)); }
}
