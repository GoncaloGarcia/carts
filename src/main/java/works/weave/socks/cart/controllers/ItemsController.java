package works.weave.socks.cart.controllers;

import com.feedzai.commons.tracing.engine.TraceUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import works.weave.socks.cart.cart.CartDAO;
import works.weave.socks.cart.cart.CartResource;
import works.weave.socks.cart.entities.Item;
import works.weave.socks.cart.item.FoundItem;
import works.weave.socks.cart.item.ItemDAO;
import works.weave.socks.cart.item.ItemResource;

import java.io.Serializable;
import java.util.List;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

@RestController
@RequestMapping(value = "/carts/{customerId:.*}/items")
public class ItemsController {
    private final Logger LOG = getLogger(getClass());

    @Autowired
    private ItemDAO itemDAO;
    @Autowired
    private CartsController cartsController;
    @Autowired
    private CartDAO cartDAO;

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/{itemId:.*}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public Item get(@PathVariable String customerId, @PathVariable String itemId, @RequestHeader HttpHeaders headers) {
        return TraceUtil.instance().newProcess(() -> {
            return new FoundItem(() -> getItems(customerId, headers), () -> new Item(itemId)).get();
        }, "Get Item", TraceUtil.instance().deserializeContext((Serializable) headers.toSingleValueMap()));
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public List<Item> getItems(@PathVariable String customerId, @RequestHeader HttpHeaders headers) {
        return TraceUtil.instance().addToTrace(() -> {
            return cartsController.get(customerId, headers).contents();
        }, "Get Items", TraceUtil.instance().deserializeContext((Serializable) headers.toSingleValueMap()));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public Item addToCart(@PathVariable String customerId, @RequestBody Item item, @RequestHeader HttpHeaders headers) {
        return TraceUtil.instance().newProcess(() -> {
            // If the item does not exist in the cart, create new one in the repository.
            FoundItem foundItem = new FoundItem(() -> cartsController.get(customerId, headers).contents(), () -> item);
            if (!foundItem.hasItem()) {
                Supplier<Item> newItem = new ItemResource(itemDAO, () -> item).create();
                LOG.debug("Did not find item. Creating item for user: " + customerId + ", " + newItem.get());
                new CartResource(cartDAO, customerId).contents().get().add(newItem).run();
                return item;
            } else {
                Item newItem = new Item(foundItem.get(), foundItem.get().quantity() + 1);
                LOG.debug("Found item in cart. Incrementing for user: " + customerId + ", " + newItem);
                updateItem(customerId, newItem, headers);
                return newItem;
            }
        }, "Add to Cart", TraceUtil.instance().deserializeContext((Serializable) headers.toSingleValueMap()));
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequestMapping(value = "/{itemId:.*}", method = RequestMethod.DELETE)
    public void removeItem(@PathVariable String customerId, @PathVariable String itemId,
                           @RequestHeader HttpHeaders headers) {
        TraceUtil.instance().newProcess(() -> {
            FoundItem foundItem = new FoundItem(() -> getItems(customerId, headers), () -> new Item(itemId));
            Item item = foundItem.get();

            LOG.debug("Removing item from cart: " + item);
            new CartResource(cartDAO, customerId).contents().get().delete(() -> item).run();

            LOG.debug("Removing item from repository: " + item);
            new ItemResource(itemDAO, () -> item).destroy().run();
        }, "Remove from Cart", TraceUtil.instance().deserializeContext((Serializable) headers.toSingleValueMap()));
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PATCH)
    public void updateItem(@PathVariable String customerId, @RequestBody Item item,
                           @RequestHeader HttpHeaders headers) {
        TraceUtil.instance().newProcess(() -> {
            // Merge old and new items
            ItemResource itemResource = new ItemResource(itemDAO, () -> get(customerId, item.itemId(), headers));
            LOG.debug("Merging item in cart for user: " + customerId + ", " + item);
            itemResource.merge(item).run();
        }, "Update Item", TraceUtil.instance().deserializeContext((Serializable) headers.toSingleValueMap()));
    }
}
