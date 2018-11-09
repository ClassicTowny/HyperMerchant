package grokswell.hypermerchant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.citizensnpcs.api.npc.NPC;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import regalowl.hyperconomy.HyperAPI;
import regalowl.hyperconomy.tradeobject.TradeObject;
import regalowl.hyperconomy.tradeobject.TradeObjectType;
import regalowl.hyperconomy.account.HyperPlayer;

import grokswell.hypermerchant.ShopTransactions;
import grokswell.util.EnchantIcons;
//import grokswell.util.EggIcons;
import grokswell.util.HyperToBukkit;
import grokswell.util.Language;
import grokswell.util.Utils;
 
public class ShopMenu implements Listener, MerchantMenu {
 
    private String shopname; //name of the shop
    private int size;
    int page_number; //the current page the player is viewing
    int item_count; //number of items in this shop
    int last_page; //the last_page number in the menu
    int sort_option; //sort-by 0=item name, 1=item type, 2=item price, 3=item quantity
    int display_zero_stock; //toggle displaying items with zero stock
    private HyperMerchantPlugin plugin;
    private Player player;
    private String inventory_name;
    private Inventory inventory;
    //private InventoryView inventory_view;
    private String[] optionNames;
    private ItemStack[] optionIcons;
	private ItemStack sorting_icon;
	
	Language L;
	HyperToBukkit hypBuk;
	ShopTransactions shop_trans;
	String economy_name;
	
	private ShopStock shopstock;
	
	NPC npc;
	double commission;
	HyperAPI hyperAPI;

	HyperPlayer hp;
	
	
    public ShopMenu(String name, int size, HyperMerchantPlugin plgn,CommandSender sender, Player plyr, NPC npc) {
    	this.sort_option=0;
    	this.display_zero_stock=1;
    	this.shopname = name;
        this.size = size;
        this.plugin = plgn;
        L=plgn.language;
        hypBuk = new HyperToBukkit();
        hyperAPI = plgn.hyperAPI;
        this.optionNames = new String[size];
        this.page_number=0;
        this.optionIcons = new ItemStack[size];
        this.player=plyr;
        this.npc = npc;
        if (this.npc!=null) {
        	this.commission = npc.getTrait(HyperMerchantTrait.class).comission*.01;
        } else {
        	this.commission = 0.0;
        }
    	this.shop_trans = new ShopTransactions(player, this.shopname, this.plugin, this);
    	this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    	UpdateSortingIcon();
    	
        String iname = (this.shopname+"<>"+player.getName());
        if (iname.length()>32) {
        	this.inventory_name = iname.substring(0, 27)+this.plugin.uniquifier.uniquify();
        } else {
        	this.inventory_name = iname;
        }

        this.inventory = Bukkit.createInventory(player, size, this.inventory_name);
        //out.println(player.getName());
        //out.println(hyperAPI.getHyperPlayer(player.getName()));
    	hp = hyperAPI.getHyperPlayer(player.getName());
    	
        economy_name = hyperAPI.getShop(this.shopname).getEconomy();
        
		this.shopstock = new ShopStock(sender, this.player, this.shopname, this.plugin, "trade");
		this.item_count=shopstock.items_count;
        double maxpages = this.item_count/45;
        this.last_page = (int) maxpages;
		this.loadPage();
		this.openMenu(this.player);
    }
    
    
    private ItemStack setItemNameAndLore(ItemStack item, String name, String[] lore) {
        ItemMeta im = item.getItemMeta();
            im.setDisplayName(ChatColor.GOLD+name);
            im.setLore(Arrays.asList(lore));
        item.setItemMeta(im);
        return item;
    }
    
    
    //Set 1 menu option. Gets called for every menu item.
    public ShopMenu setOption(int position, ItemStack icon, String name, String... info) {
    	this.optionNames[position] = name;
		try {
			this.optionIcons[position] = setItemNameAndLore(icon, name, info);
		}
		catch (Exception e){
			this.optionIcons[position] = setItemNameAndLore(new ItemStack(Material.STONE, 1), name, info);
			
		}
        return this;
    }
    
    //Set 1 menu button. Gets called for every menu button.
    public ShopMenu setOption(int position, ItemStack icon) {
    	this.optionNames[position] = icon.getItemMeta().getDisplayName();
		try {
			this.optionIcons[position] = icon;
		}
		catch (Exception e){	
		}
        return this;
    }
    
    
    @SuppressWarnings("static-access")
	public void loadPage() {
    	this.optionIcons = null;
    	this.optionIcons = new ItemStack[size];
    	this.optionNames = null;
    	this.optionNames = new String[size];
    	
    	// Populate interface button inventory slots
    	this.setOption(46, plugin.menuButtonData.back)
	    .setOption(45, plugin.menuButtonData.first_page)
	    .setOption(52, plugin.menuButtonData.forward.clone())
	    .setOption(53, plugin.menuButtonData.last_page.clone())
	    .setOption(47, plugin.menuButtonData.help1)
	    .setOption(48, plugin.menuButtonData.help2.clone())
	    .setOption(49, plugin.menuButtonData.help3.clone())
	    .setOption(50, plugin.menuButtonData.help4.clone())
	    .setOption(51, sorting_icon);
    	int count = 0;
		ArrayList<String> page=(ArrayList<String>) shopstock.pages.get(this.page_number);
		
		//Populate the shop stock slots for current page
		for (String item_name : page) {
	        // Loop through all items on this page
			double cost = 0.0;
	        double value = 0.0;
	        double stock = 0.0;
	        ItemStack stack;
        	TradeObject ho = hyperAPI.getHyperObject(item_name, economy_name, hyperAPI.getShop(shopname));
        	//TradeObject ho = hyperAPI.getHyperObject(item_name, economy_name);
        	
	        if (ho == null) {
	        	stock=0;
	        	stack=new ItemStack(Material.AIR, 1);
	        	value=0;
	        	cost=0;
	        	
	        } else if (ho.getType()==TradeObjectType.ITEM) {
	        	stock = ho.getStock();
				stack = hypBuk.getItemStack(ho.getItemStack(1));
//				if (stack.getType() == Material.MONSTER_EGG) {
//					out.println(ho.getDisplayName());
//					stack = (new EggIcons()).getIcon(ho.getDisplayName());
//				}
				
				hp.setEconomy(hyperAPI.getShop(this.shopname).getEconomy());
				value = ho.getSellPriceWithTax(1.0, hp);
				cost = ho.getBuyPriceWithTax(1.0);
				
			} else if (ho.getType()==TradeObjectType.ENCHANTMENT) {
				stock = ho.getStock();
				
				hp.setEconomy(hyperAPI.getShop(this.shopname).getEconomy());
				value = ho.getSellPriceWithTax(1.0, hp);
				cost = ho.getBuyPriceWithTax(1.0);

				stack = (new EnchantIcons()).getIcon(ho.getDisplayName(), ho.getEnchantmentLevel());

				
			} else if (ho.getType()==TradeObjectType.EXPERIENCE) {
				stock = ho.getStock();
				
				hp.setEconomy(hyperAPI.getShop(this.shopname).getEconomy());
				value = ho.getSellPriceWithTax(1.0, hp);
				cost = ho.getBuyPriceWithTax(1.0);

				stack = new ItemStack(Material.POTION, 1);
				
			} else {
				stack = new ItemStack(Material.AIR, 1);
			}
	        
	        //String status = "";
	        //if (ho == null){
	        //	status="";
	        //} else if (ho.getShopObjectStatus()!=null){
	        //	status = ChatColor.WHITE+L.II_STATUS+": "+ChatColor.DARK_PURPLE+ho.getShopObjectStatus().name().toLowerCase();
	        //}
	        
	        if (ho == null) {
				this.setOption(count, stack, "","");
	        } else {
	            HashMap<String, String> keywords = new HashMap<String, String>();
	    		keywords.put("<buyprice>",  String.format("%.2f", cost));
	    		keywords.put("<sellprice>",  String.format("%.2f", value));
	    		keywords.put("<stock>",  String.valueOf((int) stock));
	        	this.setOption(count, stack, ho.getDisplayName().replaceAll("_", " "),
	        			Utils.formatText(L.II_BUY, keywords),
	        			Utils.formatText(L.II_SELL, keywords),
	        			Utils.formatText(L.II_STOCK, keywords));
	        }
	        count++;
		}
		
		ItemStack stack;
	    while (count < size-9) {
			stack = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE,1);
	    	this.setOption(count, stack, " ", " ");
	    	count++;
	    }
	    return;
    }    
    
    
    public void nextPage() {
    	if (this.page_number < this.last_page) {
    		this.page_number++;
    		this.inventory.clear();
    		this.loadPage();
        	this.menuRefresh();
    	}
    }
    
    
    public void previousPage() {
    	if (this.page_number > 0) {
    		this.page_number--;
    		this.inventory.clear();
    		this.loadPage();
        	this.menuRefresh();
    	}
    }
    
    
    public void lastPage() {
		this.page_number = this.last_page;
		this.inventory.clear();
		this.loadPage();
    	this.menuRefresh();
    }    
    
    
    public void firstPage() {
		this.page_number = 0;
		this.inventory.clear();
		this.loadPage();
    	this.menuRefresh();
    }
    
    public void refreshPage() {
		shopstock.Refresh(sort_option, display_zero_stock);
		this.inventory.clear();
		this.loadPage();
    	this.menuRefresh();
    }
    
    
    public int itemOnCurrentPage(TradeObject ho) {
		int count = 0;
    	for (String item_name:shopstock.pages.get(this.page_number)){
    		if (item_name.equals(ho.getName())){
    			return count; 
    		}
        	count = count+1;
		}
    	return -1;
    }
    
    @SuppressWarnings("static-access")
	private void UpdateSortingIcon() {
    	this.sorting_icon = plugin.menuButtonData.help5.clone();
    	ItemMeta im;
    	List<String> lore;
    	
    	if (sorting_icon.hasItemMeta()){
    		im = sorting_icon.getItemMeta();
    	} else {
    	    im=Bukkit.getItemFactory().getItemMeta(sorting_icon.getType());
    		
    	}
    	
	    if (im.hasLore()){
	    	lore=im.getLore();
	    } else {
	    	lore = new ArrayList<String>();
	    }
	    
    	lore.add(" ");
    	
    	if (sort_option == 0){
    		lore.add(Utils.formatText(L.MM_SORTING, null)+": "+Utils.formatText(L.MM_ITEM_NAME, null));
    	} else if (sort_option == 1){
    		lore.add(Utils.formatText(L.MM_SORTING, null)+": "+Utils.formatText(L.MM_MATERIAL_NAME, null));
    	} else if (sort_option == 2){
    		lore.add(Utils.formatText(L.MM_SORTING, null)+": "+Utils.formatText(L.MM_PURCHASE_PRICE, null));
    	} else if (sort_option == 3){
    		lore.add(Utils.formatText(L.MM_SORTING, null)+": "+Utils.formatText(L.MM_SELL_PRICE_2, null));
    	} else if (sort_option == 4){
    		lore.add(Utils.formatText(L.MM_SORTING, null)+": "+Utils.formatText(L.MM_STOCK_AMOUNT, null));
    	}
    	
    	lore.add(" ");
    	
    	if (display_zero_stock == 0){
    		lore.add(Utils.formatText(L.MM_SHOW_ZERO_STOCK, null)+": "+Utils.formatText(L.G_NO, null));
    	} else {
    		lore.add(Utils.formatText(L.MM_SHOW_ZERO_STOCK, null)+": "+Utils.formatText(L.G_YES, null));
    	}
    	
    	im.setLore(lore);
    	sorting_icon.setItemMeta(im);
    }
    
    public void ToggleZeroStock() {
		if (display_zero_stock==0){
			display_zero_stock=1;
		} else display_zero_stock=0;
		
		UpdateSortingIcon();
		shopstock.Refresh(sort_option, display_zero_stock);
		this.item_count=shopstock.items_count;
        double maxpages = this.item_count/45;
        this.last_page = (int) maxpages;
		firstPage();	
    }
    
    public void Sort() {
		if (sort_option < 4){
			sort_option = sort_option+1;
		} else sort_option = 0;
		
		UpdateSortingIcon();
		shopstock.Refresh(sort_option, display_zero_stock);
		this.item_count=shopstock.items_count;
        double maxpages = this.item_count/45;
        this.last_page = (int) maxpages;
		firstPage();	
    }

    
    @SuppressWarnings("static-access")
	public void itemRefresh(int slot, TradeObject ho) {
    	hp.setEconomy(hyperAPI.getShop(this.shopname).getEconomy());
    	//out.println("ho "+ho.+" ,"+ho.getDisplayName());

        //String status = "";
        //if (ho.getShopObjectStatus()!=null){
	    //    HashMap<String, String> keywords = new HashMap<String, String>();
		//	keywords.put("<buyprice>",  ho.getShopObjectStatus().name().toLowerCase());
        //	status = Utils.formatText(L.II_STATUS, keywords);
        //}
        
        ItemStack stack;
        if (ho.getType()==TradeObjectType.ENCHANTMENT) {
        	stack = (new EnchantIcons()).getIcon(ho.getDisplayName(), ho.getEnchantmentLevel());
        }
        else if (ho.getType()==TradeObjectType.EXPERIENCE) {
			stack = new ItemStack(Material.POTION, 1);
        }
        else {
        	stack = hypBuk.getItemStack(ho.getItemStack(1));
//			if (stack.getType() == Material.MONSTER_EGG) {
//				out.println(ho.getDisplayName());
//				stack = (new EggIcons()).getIcon(ho.getDisplayName());
//			}
        }
        
        HashMap<String, String> keywords = new HashMap<String, String>();
		keywords.put("<buyprice>",  String.format("%.2f", ho.getBuyPriceWithTax(1.0)));
		keywords.put("<sellprice>",  String.format("%.2f", ho.getSellPriceWithTax(1.0, hp)));
		keywords.put("<stock>",  String.valueOf((int) ho.getStock()));
    	this.setOption(slot, stack, ho.getDisplayName().replaceAll("_", " "),
    			Utils.formatText(L.II_BUY, keywords),
    			Utils.formatText(L.II_SELL, keywords),
    			Utils.formatText(L.II_STOCK, keywords));
        
    	this.inventory.setItem(slot, this.optionIcons[slot]);

    }

    
    public void menuRefresh() {
        for (int i = 0; i < this.optionIcons.length; i++) {
            if (this.optionIcons[i] != null) {
            	this.inventory.setItem(i, this.optionIcons[i]);
            }
        }
    }
    
    
    public void openMenu(Player player) {
        for (int i = 0; i < this.optionIcons.length; i++) {
            if (this.optionIcons[i] != null) {
            	this.inventory.setItem(i, this.optionIcons[i]);
            }
        }
        player.openInventory(this.inventory);
    }
    
    
    public ShopStock getShopStock() {
        return this.shopstock;
    }
    
    
    void handleMenuCommand(int slot_num, InventoryClickEvent event){
        if (event.getCursor().getType()!=Material.AIR) {
        	event.setCancelled(true);
        	return;
        }
        if (slot_num == 46){
            this.previousPage();
        }
        else if (slot_num == 45){
        	this.firstPage();
        }
        else if (slot_num == 52){
        	this.nextPage();
        }
        else if (slot_num == 53){
        	this.lastPage();
        }
        else if (slot_num == 51){
        	if (event.isRightClick()) {
        		this.ToggleZeroStock();
        	} else this.Sort();
        }
    }
    
    
    
    void handlePlayerInventory(int slot_num, InventoryClickEvent event){
        
        if (event.isShiftClick()) {
        	event.setCancelled(true);
        	return;
        }
    }
    
    
    void handleMenuItem(int slot_num, InventoryClickEvent event){
        ItemStack item_on_cursor = event.getCursor();
        //IF THE PLAYER IS BUYING SOMETHING FROM THE SHOP
        if (item_on_cursor.getType() == Material.AIR) {
    		int qty = 1;
    		if (this.optionNames[slot_num] != null && this.optionNames[slot_num] != " ") {
                if (event.isLeftClick()) {
                	if (event.isShiftClick()) {
                		qty=8; // player pruchases stack of 8 on left+shift click
                	} else {
                		qty=1; // player purchases stack of 1 on left click
                	}
                }
                else if (event.isRightClick()) {
                	if (event.isShiftClick()) {
                		qty=this.optionIcons[slot_num].getMaxStackSize(); //player purchases max stack size on right+shift click
                    } else {
                    	qty=0;
                    }
                }
                if (qty > 0) {
	                TradeObject ho2 = this.shop_trans.Buy(this.optionNames[slot_num], qty, commission);
		            if (ho2 != null) {
		            	if (ho2.getStock()<1){
		            		this.refreshPage();
		            	} else {
		            		this.itemRefresh(slot_num, ho2);
		            	}
		            }
                } else { // player sells their own XP on right click
                	if (this.optionIcons[slot_num].getItemMeta().getDisplayName().equals(ChatColor.GOLD+"experience")) {
                		this.shop_trans.SellXP(10);
                	}
                }
			}
        }
        
        //IF THE PLAYER IS SELLING SOMETHING TO THE SHOP
        else if (item_on_cursor.getType() != Material.AIR) {
        	//TradeObject ho = hyperAPI.getHyperObject(item_on_cursor.getType().name(), hp.getHyperEconomy().getName());
        	ItemStack stack;
        	String option_name = this.optionNames[slot_num];
    		if (event.isLeftClick() && event.isShiftClick()) {
    			if (item_on_cursor.getType()==Material.ENCHANTED_BOOK) {
    				stack = this.shop_trans.Sell(item_on_cursor, option_name);
    			} else {
	    			stack = this.shop_trans.SellEnchantedItem(item_on_cursor);
    			}
    			player.setItemOnCursor(new ItemStack(Material.AIR));
    			
    			if (stack == null) {
    				player.getInventory().addItem(item_on_cursor);
    			} else {
    				player.getInventory().addItem(stack);
    			}
    			
    		} else {
				option_name=option_name.replace(" ", "_");
	    		stack = this.shop_trans.Sell(item_on_cursor, option_name);
				player.setItemOnCursor(new ItemStack(Material.AIR));
				player.getInventory().addItem(stack);
    		}    
			return;

    	}
    }    
    
    @EventHandler(priority=EventPriority.HIGHEST)
    void onInventoryClick(InventoryClickEvent event) {
    	if (player.getGameMode().compareTo(GameMode.CREATIVE) != 0) {
    		onInventoryClickOrCreative(event);
    	} 
    	else if (player.hasPermission("creative.hypermerchant")){
    		onInventoryClickOrCreative(event);
    	} else {
    		event.setCancelled(true);
    	}
    }
    
    
    void onInventoryClickOrCreative(InventoryClickEvent event) {

        if (event.getInventory().getTitle().equals(this.inventory_name)) {
    		int slot_num = event.getRawSlot();
            if (slot_num < size) {
            	event.setCancelled(true);
            }
            
            if (slot_num < size-9 && slot_num >= 0){
            	this.handleMenuItem(slot_num, event);
            }
            else if (slot_num >= size-9 && slot_num < size) {
            	this.handleMenuCommand(slot_num, event);
            }
            else if (slot_num >= size) {
            	this.handlePlayerInventory(slot_num, event);
            }
        }
    }
    
    
    @EventHandler(priority=EventPriority.HIGHEST)
    void onInventoryClose(InventoryCloseEvent event) {
    	if (event.getPlayer().equals(player)) {
	    	if (this.npc != null) {
	    		this.npc.getTrait(HyperMerchantTrait.class).customer_menus.put(player.getName(), null);
	    		this.npc.getTrait(HyperMerchantTrait.class).customer_menus.remove(player.getName());
	    		this.npc.getTrait(HyperMerchantTrait.class).onFarewell(player);
	    	}
	    	this.destroy();
    	}
    }
    
    
    @EventHandler(priority=EventPriority.HIGHEST)
    void onInventoryMoveItem(InventoryMoveItemEvent event) {
    	if (event.getSource().equals(this.inventory)) {
    		event.setCancelled(true);
    	}
    }
    
    
    @EventHandler(priority=EventPriority.HIGHEST)
    void onInventoryDrag(InventoryDragEvent event) {
    	if (event.getInventory().equals(this.inventory)) {
    		event.setCancelled(true);
    	}
    }
    
    
    public void destroy() {
    	HandlerList.unregisterAll(this);
        this.plugin = null;
        this.optionNames = null;
        this.optionIcons = null;
        this.inventory = null;
        //this.inventory_view = null;
        this.shop_trans = null;
        this.inventory_name = null;
        this.shopstock = null;
    }
}