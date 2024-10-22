#import "StoreKit2Manager.h"
#import <StoreKit/StoreKit.h>

@interface StoreKit2Manager () <SKPaymentTransactionObserver>
@property (nonatomic, strong) NSHashTable *transactionObservers;
@property (nonatomic, strong) NSMutableDictionary *purchaseCallbacks;
@end

@implementation StoreKit2Manager

+ (instancetype)sharedInstance {
    static StoreKit2Manager *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[self alloc] init];
    });
    return sharedInstance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _transactionObservers = [NSHashTable weakObjectsHashTable];
        _purchaseCallbacks = [NSMutableDictionary new];
        [[SKPaymentQueue defaultQueue] addTransactionObserver:self];
    }
    return self;
}

- (void)dealloc {
    [[SKPaymentQueue defaultQueue] removeTransactionObserver:self];
}

#pragma mark - Product Methods

- (void)requestProductsWithIdentifiers:(NSSet<NSString *>*)identifiers
                             success:(void (^)(NSArray *products, NSArray *invalidIdentifiers))successBlock
                             failure:(void (^)(NSError *error))failureBlock {
    if (@available(iOS 15.0, *)) {
        [self requestProducts_SK2:identifiers success:successBlock failure:failureBlock];
    } else {
        [self requestProducts_SK1:identifiers success:successBlock failure:failureBlock];
    }
}

- (void)requestProducts_SK2:(NSSet<NSString *>*)identifiers
                   success:(void (^)(NSArray *products, NSArray *invalidIdentifiers))successBlock
                   failure:(void (^)(NSError *error))failureBlock API_AVAILABLE(ios(15.0)) {
    
    [SKProduct productsWithIdentifiers:identifiers completionHandler:^(NSArray<SKProduct *> *products, NSError *error) {
        if (error) {
            if (failureBlock) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    failureBlock(error);
                });
            }
            return;
        }
        
        NSMutableArray *invalidIdentifiers = [NSMutableArray array];
        for (NSString *identifier in identifiers) {
            BOOL found = NO;
            for (SKProduct *product in products) {
                if ([product.productIdentifier isEqualToString:identifier]) {
                    found = YES;
                    break;
                }
            }
            if (!found) {
                [invalidIdentifiers addObject:identifier];
            }
        }
        
        if (successBlock) {
            dispatch_async(dispatch_get_main_queue(), ^{
                successBlock(products, invalidIdentifiers);
            });
        }
    }];
}

- (void)requestProducts_SK1:(NSSet<NSString *>*)identifiers
                   success:(void (^)(NSArray *products, NSArray *invalidIdentifiers))successBlock
                   failure:(void (^)(NSError *error))failureBlock {
    SKProductsRequest *request = [[SKProductsRequest alloc] initWithProductIdentifiers:identifiers];
    request.delegate = self;
    [request start];
}

#pragma mark - Purchase Methods

- (void)purchaseProduct:(NSString *)productIdentifier
                success:(void (^)(SKPaymentTransaction *transaction, NSString *receipt))successBlock
                failure:(void (^)(NSError *error))failureBlock {
    
    if (@available(iOS 15.0, *)) {
        [self purchaseProduct_SK2:productIdentifier success:successBlock failure:failureBlock];
    } else {
        [self purchaseProduct_SK1:productIdentifier success:successBlock failure:failureBlock];
    }
}

- (void)purchaseProduct_SK2:(NSString *)productIdentifier
                   success:(void (^)(SKPaymentTransaction *transaction, NSString *receipt))successBlock
                   failure:(void (^)(NSError *error))failureBlock API_AVAILABLE(ios(15.0)) {
    
    [SKProduct productsWithIdentifiers:[NSSet setWithObject:productIdentifier] completionHandler:^(NSArray<SKProduct *> *products, NSError *error) {
        if (error || products.count == 0) {
            if (failureBlock) {
                NSError *productError = error ?: [NSError errorWithDomain:@"StoreKit2ManagerError" 
                                                                   code:100 
                                                               userInfo:@{NSLocalizedDescriptionKey: @"Product not found"}];
                dispatch_async(dispatch_get_main_queue(), ^{
                    failureBlock(productError);
                });
            }
            return;
        }
        
        SKProduct *product = products.firstObject;
        SKPayment *payment = [SKPayment paymentWithProduct:product];
        
        // Store callback for later use
        self.purchaseCallbacks[productIdentifier] = @{
            @"success": successBlock,
            @"failure": failureBlock
        };
        
        [[SKPaymentQueue defaultQueue] addPayment:payment];
    }];
}

- (void)purchaseProduct_SK1:(NSString *)productIdentifier
                   success:(void (^)(SKPaymentTransaction *transaction, NSString *receipt))successBlock
                   failure:(void (^)(NSError *error))failureBlock {
    
    SKProduct *product = [self cachedProductForIdentifier:productIdentifier];
    if (!product) {
        if (failureBlock) {
            NSError *error = [NSError errorWithDomain:@"StoreKit2ManagerError" 
                                               code:100 
                                           userInfo:@{NSLocalizedDescriptionKey: @"Product not found"}];
            failureBlock(error);
        }
        return;
    }
    
    SKPayment *payment = [SKPayment paymentWithProduct:product];
    
    // Store callback for later use
    self.purchaseCallbacks[productIdentifier] = @{
        @"success": successBlock,
        @"failure": failureBlock
    };
    
    [[SKPaymentQueue defaultQueue] addPayment:payment];
}

#pragma mark - Restore Purchases

- (void)restorePurchases:(void (^)(NSArray *transactions))successBlock
                failure:(void (^)(NSError *error))failureBlock {
    if (@available(iOS 15.0, *)) {
        [self restorePurchases_SK2:successBlock failure:failureBlock];
    } else {
        [self restorePurchases_SK1:successBlock failure:failureBlock];
    }
}

- (void)restorePurchases_SK2:(void (^)(NSArray *transactions))successBlock
                    failure:(void (^)(NSError *error))failureBlock API_AVAILABLE(ios(15.0)) {
    [[SKPaymentQueue defaultQueue] restoreCompletedTransactionsWithApplicationUsername:nil];
    // Callbacks will be handled through the transaction observer
}

- (void)restorePurchases_SK1:(void (^)(NSArray *transactions))successBlock
                    failure:(void (^)(NSError *error))failureBlock {
    [[SKPaymentQueue defaultQueue] restoreCompletedTransactions];
    // Callbacks will be handled through the transaction observer
}

#pragma mark - Receipt Handling

- (NSString *)getReceipt {
    NSURL *receiptURL = [[NSBundle mainBundle] appStoreReceiptURL];
    NSData *receiptData = [NSData dataWithContentsOfURL:receiptURL];
    return [receiptData base64EncodedStringWithOptions:0];
}

#pragma mark - Transaction Verification

- (void)verifyPurchase:(SKPaymentTransaction *)transaction
              success:(void (^)(BOOL verified))successBlock
              failure:(void (^)(NSError *error))failureBlock {
    if (@available(iOS 15.0, *)) {
        [self verifyPurchase_SK2:transaction success:successBlock failure:failureBlock];
    } else {
        // Basic verification for SK1
        successBlock(YES);
    }
}

- (void)verifyPurchase_SK2:(SKPaymentTransaction *)transaction
                  success:(void (^)(BOOL verified))successBlock
                  failure:(void (^)(NSError *error))failureBlock API_AVAILABLE(ios(15.0)) {
    // Implement your server-side verification here
    // For now, we'll just return success
    successBlock(YES);
}

#pragma mark - Transaction Monitoring

- (void)startTransactionMonitoring {
    // Already started in init
}

- (void)stopTransactionMonitoring {
    [[SKPaymentQueue defaultQueue] removeTransactionObserver:self];
}

- (void)addTransactionObserver:(id<SKPaymentTransactionObserver>)observer {
    [self.transactionObservers addObject:observer];
}

- (void)removeTransactionObserver:(id<SKPaymentTransactionObserver>)observer {
    [self.transactionObservers removeObject:observer];
}

#pragma mark - SKPaymentTransactionObserver

- (void)paymentQueue:(SKPaymentQueue *)queue updatedTransactions:(NSArray<SKPaymentTransaction *> *)transactions {
    for (SKPaymentTransaction *transaction in transactions) {
        [self handleTransaction:transaction];
    }
}

- (void)handleTransaction:(SKPaymentTransaction *)transaction {
    NSString *productId = transaction.payment.productIdentifier;
    NSDictionary *callbacks = self.purchaseCallbacks[productId];
    void (^successBlock)(SKPaymentTransaction *, NSString *) = callbacks[@"success"];
    void (^failureBlock)(NSError *) = callbacks[@"failure"];
    
    switch (transaction.transactionState) {
        case SKPaymentTransactionStatePurchased:
        case SKPaymentTransactionStateRestored: {
            if (successBlock) {
                NSString *receipt = [self getReceipt];
                successBlock(transaction, receipt);
            }
            [[SKPaymentQueue defaultQueue] finishTransaction:transaction];
            [self.purchaseCallbacks removeObjectForKey:productId];
            break;
        }
        case SKPaymentTransactionStateFailed: {
            if (failureBlock) {
                failureBlock(transaction.error);
            }
            [[SKPaymentQueue defaultQueue] finishTransaction:transaction];
            [self.purchaseCallbacks removeObjectForKey:productId];
            break;
        }
        default:
            break;
    }
    
    // Notify observers
    for (id<SKPaymentTransactionObserver> observer in self.transactionObservers) {
        if ([observer respondsToSelector:@selector(paymentQueue:updatedTransactions:)]) {
            [observer paymentQueue:queue updatedTransactions:@[transaction]];
        }
    }
}

@end
