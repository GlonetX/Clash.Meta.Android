#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "libclash.h"

JNIEXPORT jstring JNICALL Java_com_github_kr328_clash_core_bridge_BackupBridge_cryptFile(JNIEnv *env,jobject self,jstring input,jstring output,jbyteArray password,jboolean decrypt) {
 const char *in=(*env)->GetStringUTFChars(env,input,NULL);
 const char *out=(*env)->GetStringUTFChars(env,output,NULL);
 jsize length=(*env)->GetArrayLength(env,password);
 unsigned char *secret=malloc((size_t)length+1);
 if(!secret){(*env)->ReleaseStringUTFChars(env,input,in);(*env)->ReleaseStringUTFChars(env,output,out);return (*env)->NewStringUTF(env,"Out of memory");}
 (*env)->GetByteArrayRegion(env,password,0,length,(jbyte*)secret);
 char *error=backupCryptFile((char*)in,(char*)out,secret,length,decrypt?1:0);
 volatile unsigned char *wipe=secret;for(jsize i=0;i<length;i++)wipe[i]=0;free(secret);
 (*env)->ReleaseStringUTFChars(env,input,in);(*env)->ReleaseStringUTFChars(env,output,out);
 if(!error)return NULL;
 jstring result=(*env)->NewStringUTF(env,error);free(error);return result;
}
JNIEXPORT jstring JNICALL Java_com_github_kr328_clash_core_bridge_BackupBridge_providerInventory(JNIEnv *env,jobject self,jstring path,jstring keys,jstring override) {
 const char *p=(*env)->GetStringUTFChars(env,path,NULL),*k=(*env)->GetStringUTFChars(env,keys,NULL),*o=(*env)->GetStringUTFChars(env,override,NULL);
 char *json=backupProviderInventory((char*)p,(char*)k,(char*)o);
 (*env)->ReleaseStringUTFChars(env,path,p);(*env)->ReleaseStringUTFChars(env,keys,k);(*env)->ReleaseStringUTFChars(env,override,o);
 jstring result=(*env)->NewStringUTF(env,json);free(json);return result;
}
JNIEXPORT void JNICALL Java_com_github_kr328_clash_core_bridge_BackupBridge_beginSnapshot(JNIEnv *env,jobject self){backupBeginSnapshot();}
JNIEXPORT void JNICALL Java_com_github_kr328_clash_core_bridge_BackupBridge_endSnapshot(JNIEnv *env,jobject self){backupEndSnapshot();}
