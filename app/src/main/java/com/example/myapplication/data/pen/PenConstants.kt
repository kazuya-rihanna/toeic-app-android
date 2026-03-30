package com.example.myapplication.data.pen

object PenConstants {
    const val ACTION_PEN_MESSAGE = "action_pen_message"
    const val ACTION_PEN_DOT = "action_pen_dot"
    const val ACTION_OFFLINE_STROKES = "action_offline_strokes"
    const val EXTRA_PEN_MSG_TYPE = "message_type"
    const val EXTRA_DOT = "dot"
    
    // Connection Messages (based on SDK PenCtrl)
    const val PEN_CONNECTION_SUCCESS = 1
    const val PEN_CONNECTION_FAILURE = 0
    const val PEN_DISCONNECTED = 2
    const val PEN_AUTHORIZED = 3
}
