package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.support.annotation.NonNull;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JinglePacket extends IqPacket {

    //TODO add support for groups: https://xmpp.org/extensions/xep-0338.html

    private JinglePacket() {
        super();
    }

    public JinglePacket(final Action action, final String sessionId) {
        super(TYPE.SET);
        final Element jingle = addChild("jingle", Namespace.JINGLE);
        jingle.setAttribute("sid", sessionId);
        jingle.setAttribute("action", action.toString());
    }

    public static JinglePacket upgrade(final IqPacket iqPacket) {
        Preconditions.checkArgument(iqPacket.hasChild("jingle", Namespace.JINGLE));
        final JinglePacket jinglePacket = new JinglePacket();
        jinglePacket.setAttributes(iqPacket.getAttributes());
        jinglePacket.setChildren(iqPacket.getChildren());
        return jinglePacket;
    }

    //TODO deprecate this somehow and make file transfer fail if there are multiple (or something)
    public Content getJingleContent() {
        final Element content = getJingleChild("content");
        return content == null ? null : Content.upgrade(content);
    }

    public Group getGroup() {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        final Element group = jingle.findChild("group", Namespace.JINGLE_APPS_GROUPING);
        return group == null ? null : Group.upgrade(group);
    }

    public void addGroup(final Group group) {
        this.addJingleChild(group);
    }

    public Map<String, Content> getJingleContents() {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        ImmutableMap.Builder<String, Content> builder = new ImmutableMap.Builder<>();
        for (final Element child : jingle.getChildren()) {
            if ("content".equals(child.getName())) {
                final Content content = Content.upgrade(child);
                builder.put(content.getContentName(), content);
            }
        }
        return builder.build();
    }

    public void addJingleContent(final Content content) { //take content interface
        addJingleChild(content);
    }

    public Reason getReason() {
        final Element reason = getJingleChild("reason");
        return reason == null ? null : Reason.upgrade(reason);
    }

    public void setReason(final Reason reason) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        jingle.addChild(reason);
    }

    //RECOMMENDED for session-initiate, NOT RECOMMENDED otherwise
    public void setInitiator(final Jid initiator) {
        Preconditions.checkArgument(initiator.isFullJid(), "initiator should be a full JID");
        findChild("jingle", Namespace.JINGLE).setAttribute("initiator", initiator.toEscapedString());
    }

    //RECOMMENDED for session-accept, NOT RECOMMENDED otherwise
    public void setResponder(Jid responder) {
        Preconditions.checkArgument(responder.isFullJid(), "responder should be a full JID");
        findChild("jingle", Namespace.JINGLE).setAttribute("responder", responder.toEscapedString());
    }

    public Element getJingleChild(final String name) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        return jingle == null ? null : jingle.findChild(name);
    }

    public void addJingleChild(final Element child) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        jingle.addChild(child);
    }

    public String getSessionId() {
        return findChild("jingle", Namespace.JINGLE).getAttribute("sid");
    }

    public Action getAction() {
        return Action.of(findChild("jingle", Namespace.JINGLE).getAttribute("action"));
    }

    public enum Action {
        CONTENT_ACCEPT,
        CONTENT_ADD,
        CONTENT_MODIFY,
        CONTENT_REJECT,
        CONTENT_REMOVE,
        DESCRIPTION_INFO,
        SECURITY_INFO,
        SESSION_ACCEPT,
        SESSION_INFO,
        SESSION_INITIATE,
        SESSION_TERMINATE,
        TRANSPORT_ACCEPT,
        TRANSPORT_INFO,
        TRANSPORT_REJECT,
        TRANSPORT_REPLACE;

        public static Action of(final String value) {
            //TODO handle invalid
            return Action.valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, value));
        }

        @Override
        @NonNull
        public String toString() {
            return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, super.toString());
        }
    }
}
