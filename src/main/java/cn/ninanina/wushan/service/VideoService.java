package cn.ninanina.wushan.service;

import cn.ninanina.wushan.domain.Comment;
import cn.ninanina.wushan.domain.User;
import cn.ninanina.wushan.domain.VideoDetail;
import cn.ninanina.wushan.domain.VideoDir;
import com.sun.istack.NotNull;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 提供所有视频相关API。
 * <p>所有返回的视频详情都包含真实可用的封面链接,但列表信息不包含视频实时链接。
 * <p>获取视频的实时链接需要单独请求。
 * <p>重点：xvideos的视频链接有效时长为3小时。所以，我们规定超过2.5小时的链接为失效。
 */
public interface VideoService {
    /**
     * 获取推荐视频列表，返回10个。目前采用最简单的实现方式：
     * <p>首先取validVideoCache里面的值，即当前有效视频。有效视频取完后，
     * <p>然后根据用户收藏、下载、浏览过的视频推荐，
     * <p>如果没有任何记录或者记录很少，则从选定的精华视频中推荐。
     *
     * @param user   用户信息
     * @param appKey 用来区分app
     * @return 推荐视频列表，视频链接不一定有效
     */
    List<VideoDetail> recommendVideos(User user, @Nonnull String appKey, @Nonnull String type, @Nonnull Integer limit);

    /**
     * 获取指定视频的有效信息，即更新视频链接，当客户端请求视频详情，并且视频链接失效时才调用。
     * <p>视频实际有效为3小时，我们规定超过2.5小时则失效。
     * <p>如果数据库/缓存中的视频链接还有效，则不打开页面重新请求，否则就调用selenium的接口进行更新。
     *
     * @param videoId 视频id
     * @return 视频当前有效信息
     */
    VideoDetail getVideoDetail(@Nonnull Long videoId, User user);

    /**
     * 首先获取一级相关视频，一般有20-50个，获取完了之后获取二级相关，一般有1000个左右。
     *
     * @param videoId 视频id
     * @param limit   相关视频数量
     * @return 相关视频列表
     */
    List<VideoDetail> relatedVideos(@Nonnull Long videoId, @Nonnull Integer offset, @Nonnull Integer limit);

    /**
     * 根据关键词搜索视频。一般用户都是用中文搜索，根据标题和标签进行匹配，并且同时进行中英文匹配。后期会考虑根据视频评论来匹配
     * 视频标题和标签翻译都来根据有道翻译。英译汉能力有道>谷歌>百度>others
     *
     * @param query 关键词，可以中文可以英文
     * @return 匹配的视频列表
     */
    List<VideoDetail> search(@Nonnull String query, @Nonnull Integer offset, @Nonnull Integer limit);

    /**
     * 发表视频评论
     *
     * @param user     当前用户
     * @param videoId  视频id
     * @param content  评论内容
     * @param parentId 评论父id,可为空
     * @return 生成的评论信息
     */
    Comment commentOn(@Nonnull User user, @Nonnull Long videoId, @Nonnull String content, @Nullable Long parentId);

    /**
     * 新建收藏夹
     *
     * @param user 用户
     * @param name 收藏夹名字
     * @return 创建结果
     */
    VideoDir createDir(@Nonnull User user, @Nonnull String name);

    /**
     * 查看收藏夹是否属于用户
     *
     * @param user  用户
     * @param dirId 收藏夹id
     * @return 属于返回true，不属于返回false
     */
    Boolean possessDir(@Nonnull User user, @Nonnull Long dirId);

    /**
     * 删除收藏夹
     *
     * @param user 用户
     * @param id   收藏夹id
     */
    void removeDir(@Nonnull Long id);

    /**
     * 重命名收藏夹
     *
     * @param id   收藏夹id
     * @param name 新名字
     */
    VideoDir renameDir(@Nonnull Long id, @Nonnull String name);

    /**
     * 获取用户的收藏文件夹列表
     *
     * @param user 用户
     */
    List<VideoDir> collectedDirs(User user);

    /**
     * 收藏视频/取消收藏
     *
     * @param user    用户
     * @param videoId 视频id
     * @param dirId   文件夹id
     * @return true表示收藏成功，false表示已收藏过
     */
    Boolean collect(@Nonnull Long videoId, @Nonnull Long dirId);

    /**
     * 取消收藏
     *
     * @param videoId 视频id
     * @param dirId   收藏夹id
     * @return true表示取消成功，false表示收藏夹为空或者收藏夹不包含给定video
     */
    Boolean cancelCollect(@Nonnull Long videoId, @Nonnull Long dirId);

    /**
     * 获取用户看过的视频列表，分段获取
     *
     * @param user   用户
     * @param offset offset
     * @param limit  limit
     */
    List<VideoDetail> viewedVideos(@Nonnull User user, @Nonnull Integer offset, @Nonnull Integer limit);

    /**
     * 下载视频，做个记录方便推荐。因为下载的视频一定是用户最喜欢的，权重比收藏还要高。
     *
     * @param user    下载的用户
     * @param videoId 下载的视频id
     */
    void download(@Nonnull User user, @Nonnull Long videoId);

    /**
     * 用户退出视频播放
     */
    void exitDetail(@Nonnull Long videoId);

    /**
     * 指定视频的当前观众数
     *
     * @param videoId 视频id
     * @return 观众人数
     */
    int audiences(@Nonnull Long videoId);

    /**
     * 获取当前在线视频排行，根据观众数降序排列
     *
     * @param limit 限制数量
     * @return Pair列表，左为video详情，右为当前观看人数。
     */
    List<Pair<VideoDetail, Integer>> onlineRank(@Nonnull Integer limit);

}
