package org.ssssssss.magicapi.provider;

import org.ssssssss.magicapi.model.MagicNotify;

/**
 * 接口通知发送处理接口
 *
 * @author mxd
 */
public interface MagicNotifyService {

	/**
	 * 发送通知
	 *
	 * @param magicNotify 通知对象
	 */
	void sendNotify(MagicNotify magicNotify);

}
