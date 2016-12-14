package io.slychat.messenger.android.activites

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.WindowManager
import android.view.LayoutInflater
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import org.slf4j.LoggerFactory
import android.widget.AdapterView.OnItemClickListener
import io.slychat.messenger.android.MainActivity
import io.slychat.messenger.android.activites.services.impl.ContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.GroupServiceImpl
import io.slychat.messenger.android.activites.services.impl.MessengerServiceImpl
import io.slychat.messenger.android.activites.services.impl.SettingsServiceImpl
import io.slychat.messenger.android.formatTimeStamp
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.LoginEvent
import io.slychat.messenger.services.PageType
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import rx.Subscription

class RecentChatActivity : AppCompatActivity(), BaseActivityInterface, NavigationView.OnNavigationItemSelectedListener {
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp

    private lateinit var recentChatList: LinearLayout
    private lateinit var contactFloatBtn: FloatingActionButton

    private var loginListener: Subscription? = null
    private lateinit var messengerService: MessengerServiceImpl
    private lateinit var groupService: GroupServiceImpl
    private lateinit var contactService: ContactServiceImpl
    private lateinit var settingsService: SettingsServiceImpl

    private var recentNodeData: MutableMap<UserId, Int> = mutableMapOf()
    private var groupRecentNodeData: MutableMap<GroupId, Int> = mutableMapOf()

    data class RecentChatData(
            val id: ConversationId,
            var groupName: String?,
            var lastSpeakerName: String,
            var lastTimestamp: Long,
            var lastMessage: String,
            var unreadMessageCount: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_recent_chat)

        app = AndroidApp.get(this)
        messengerService = MessengerServiceImpl(this)
        groupService = GroupServiceImpl(this)
        contactService = ContactServiceImpl(this)
        settingsService = SettingsServiceImpl(this)

        val actionBar = findViewById(R.id.recent_chat_toolbar) as Toolbar
        actionBar.title = "  Sly Chat"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)
            actionBar.logo = getDrawable(R.drawable.ic_launcher)
        else
            actionBar.logo = resources.getDrawable(R.drawable.ic_launcher)

        setSupportActionBar(actionBar)

        val navigationView = findViewById(R.id.nav_recent_chat_view) as NavigationView
        navigationView.setNavigationItemSelectedListener(this)

        recentChatList = findViewById(R.id.recent_chat_container) as LinearLayout
        contactFloatBtn = findViewById(R.id.contact_float_btn) as FloatingActionButton

        val drawerName = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_name) as TextView
        val drawerEmail = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_email) as TextView

        drawerEmail.text = app.accountInfo?.email
        drawerName.text = app.accountInfo?.name

        createEventListeners()
    }

    private fun init () {
        recentChatList.removeAllViews()

        messengerService.fetchAllRecentChat() successUi {
            displayRecentChat(it)
            showInviteFriends()
        } failUi {
            log.debug("failed to fetch recent chats")
        }

        setListeners()
    }

    private fun createEventListeners () {
        contactFloatBtn.setOnClickListener {
            startActivity(Intent(baseContext, ContactActivity::class.java))
        }
    }

    private fun displayRecentChat(data: List<RecentChatData>) {
        data.forEach {
            recentChatList.addView(createRecentChatView(it))
        }
    }

    private fun createRecentChatView(data: RecentChatData): View {
        val node = LayoutInflater.from(this).inflate(R.layout.recent_chat_node_layout, recentChatList, false)
        val nameView = node.findViewById(R.id.recent_chat_contact_name) as TextView
        val messageView = node.findViewById(R.id.recent_chat_contact_message) as TextView
        val timespanView = node.findViewById(R.id.recent_chat_contact_time) as TextView
        var name = ""
        if (data.groupName != null)
            name += "(" + data.groupName + ") "
        name += data.lastSpeakerName
        nameView.text = name
        messageView.text = data.lastMessage

        val time: String
        time = formatTimeStamp(data.lastTimestamp)
        timespanView.text = time

        if (data.unreadMessageCount > 0) {
            val badge = node.findViewById(R.id.new_message_badge) as LinearLayout
            val amount = badge.findViewById(R.id.new_message_quantity) as TextView
            badge.visibility = LinearLayout.VISIBLE
            amount.text = data.unreadMessageCount.toString()
        }

        val nodeId = View.generateViewId()
        node.id = nodeId

        if (data.id is ConversationId.User) {
            node.setOnClickListener {
                val intent = Intent(baseContext, ChatActivity::class.java)
                intent.putExtra("EXTRA_ISGROUP", false)
                intent.putExtra("EXTRA_ID", data.id.id.long)
                startActivity(intent)
            }
            recentNodeData.put(data.id.id, nodeId)
        }
        else if (data.id is ConversationId.Group) {
            node.setOnClickListener {
                val intent = Intent(baseContext, ChatActivity::class.java)
                intent.putExtra("EXTRA_ISGROUP", true)
                intent.putExtra("EXTRA_ID", data.id.id.string)
                startActivity(intent)
            }
            groupRecentNodeData.put(data.id.id, nodeId)
        }

        return node
    }

    private fun showInviteFriends() {
        contactService.getContactCount() successUi { count ->
            val inviteNode = findViewById(R.id.recent_chat_invite_node) as LinearLayout
            if (count < 5 && settingsService.getShowInviteEnabled()) {
                inviteNode.visibility = View.VISIBLE
                inviteNode.setOnClickListener {
                    startActivity(Intent(baseContext, InviteFriendsActivity::class.java))
                }
            }
            else
                inviteNode.visibility = View.GONE
        } failUi {
            log.debug("Failed to check the total count of contacts")
        }
    }

    fun onNewMessage(data: ConversationMessage) {
        val conversationId = data.conversationId
        when (conversationId) {
            is ConversationId.User -> {
                val conversation = messengerService.conversations[conversationId.id]
                if (conversation != null)
                    updateSingleRecentChatNode(conversation)
            }
            is ConversationId.Group -> {
                val conversation = messengerService.groupConversations[conversationId.id]
                if (conversation != null)
                    updateGroupRecentChatNode(conversation)
                log.debug(conversationId.id.toString())
            }
        }
    }

    private fun updateSingleRecentChatNode(conversation: UserConversation) {
        val userId = conversation.contact.id
        val nodeId = recentNodeData[userId]
        if (nodeId !== null) {
            recentChatList.removeView(findViewById(nodeId))
        }

        val cId = userId.toConversationId()

        recentChatList.addView(createRecentChatView(RecentChatData(
                cId,
                null,
                conversation.contact.name,
                conversation.info.lastTimestamp as Long,
                conversation.info.lastMessage as String,
                conversation.info.unreadMessageCount
                )), 0)
    }

    private fun updateGroupRecentChatNode(conversation: GroupConversation) {
        val groupId = conversation.group.id
        val nodeId = groupRecentNodeData[groupId]
        if (nodeId !== null) {
            recentChatList.removeView(findViewById(nodeId))
        }

        val cId = groupId.toConversationId()

        var speakerName = ""
        val lastSpeakerId = conversation.info.lastSpeaker
        if (lastSpeakerId == null)
            speakerName = "You"
        else {
            val contactInfo = messengerService.getContactInfo(lastSpeakerId)
            if (contactInfo != null)
                speakerName = contactInfo.name
        }

        recentChatList.addView(createRecentChatView(RecentChatData(
                cId,
                conversation.group.name,
                speakerName,
                conversation.info.lastTimestamp as Long,
                conversation.info.lastMessage as String,
                conversation.info.unreadMessageCount
        )), 0)
    }

    private fun setListeners () {
        loginListener?.unsubscribe()
        loginListener = app.app.loginEvents.subscribe {
            handleLoginEvent(it)
        }

        messengerService.addNewMessageListener({ onNewMessage(it) })
    }

    private fun clearListners () {
        loginListener?.unsubscribe()
        messengerService.clearListeners()
    }

    private fun handleLoginEvent (event: LoginEvent) {
        when (event) {
            is LoginEvent.LoggedOut -> { processLogout() }
        }
    }

    private fun processLogout () {
        startActivity(Intent(baseContext, MainActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        val drawer = findViewById(R.id.recent_chat_drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.layout_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_menu -> {
                val mDrawerLayout = findViewById(R.id.recent_chat_drawer_layout) as DrawerLayout
                mDrawerLayout.openDrawer(Gravity.END)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menu_profile -> { startActivity(Intent(baseContext, ProfileActivity::class.java)) }
            R.id.menu_add_contact -> { startActivity(Intent(baseContext, AddContactActivity::class.java)) }
            R.id.menu_create_group -> { startActivity(Intent(baseContext, CreateGroupActivity::class.java)) }
            R.id.menu_blocked_contacts -> { }
            R.id.menu_settings -> { startActivity(Intent(baseContext, SettingsActivity::class.java)) }
            R.id.menu_share -> { startActivity(Intent(baseContext, InviteFriendsActivity::class.java)) }
            R.id.menu_feedback -> { startActivity(Intent(baseContext, FeedbackActivity::class.java)) }
            R.id.menu_logout -> { app.app.logout() }
        }

        val drawer = findViewById(R.id.recent_chat_drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.END)
        return true
    }

    private fun dispatchEvent () {
        app.dispatchEvent("PageChange", PageType.CONTACTS, "")
    }

    override fun setAppActivity() {
        log.debug("set ui visible")
        app.setCurrentActivity(this, true)
    }

    override fun clearAppActivity() {
        log.debug("set ui hidden")
        app.setCurrentActivity(this, false)
    }

    override fun onStart() {
        super.onStart()
        log.debug("onStart")
    }

    override fun onPause() {
        super.onPause()
        log.debug("onPause")
        clearAppActivity()
        clearListners()
    }

    override fun onResume() {
        super.onResume()
        init()
        dispatchEvent()
        setAppActivity()
        log.debug("onResume")
    }

    override fun onStop() {
        super.onStop()
        log.debug("onStop")
        clearListners()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearAppActivity()
        log.debug("onDestroy")
    }
}