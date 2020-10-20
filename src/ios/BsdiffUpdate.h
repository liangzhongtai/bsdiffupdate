//
//  OnekeyLogin.h
//  网优助手
//
//  Created by 梁仲太 on 2020/6/22.
//

#import <Cordova/CDV.h>

// 下载增量包
static NSInteger const BSDIFF_DOWNLOAD_PACKAGE = 0;


@interface BsdiffUpdate : CDVPlugin

-(void)coolMethod:(CDVInvokedUrlCommand *)command;
-(void)successWithMessage:(NSArray *)messages;
-(void)faileWithMessage:(NSString *)message;

@end
