#import <Foundation/Foundation.h>
#import <StoreKit/StoreKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface StoreKit2Manager : NSObject

+ (instancetype)sharedInstance;

// Product methods
- (void)requestProductsWithIdentifiers:(NSSet<NSString *>*)identifiers
                             success:(void (^)(NSArray *products, NSArray *invalidIdentifiers))successBlock
                             failure:(void (^)(NSError *error))failureBlock;

// Purchase methods
- (void)purchaseProduct:(NSString *)productIdentifier
                success:(void (^)(SKPaymentTransaction *transaction, NSString *receipt))successBlock
                failure:(void (^)(NSError *error))failureBlock API_AVAILABLE(ios(15.0));

// Restore purchases
- (void)restorePurchases:(void (^)(NSArray *transactions))successBlock
                failure:(void (^)(NSError *error))failureBlock API_AVAILABLE(ios(15.0));

// Receipt handling
- (NSString *)getReceipt;

// Transaction verification
- (void)verifyPurchase:(SKPaymentTransaction *)transaction 
              success:(void (^)(BOOL verified))successBlock
              failure:(void (^)(NSError *error))failureBlock API_AVAILABLE(ios(15.0));

// Transaction monitoring
- (void)startTransactionMonitoring;
- (void)stopTransactionMonitoring;
- (void)addTransactionObserver:(id<SKPaymentTransactionObserver>)observer;
- (void)removeTransactionObserver:(id<SKPaymentTransactionObserver>)observer;

@end

NS_ASSUME_NONNULL_END
