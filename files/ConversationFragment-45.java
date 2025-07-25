case MucOptions.ERROR_NICK_IN_USE:
    showSnackbar(R.string.nick_in_use, R.string.edit, clickToMuc);
    break;
case MucOptions.ERROR_ROOM_NOT_FOUND:
    showSnackbar(R.string.conference_not_found, R.string.leave, leaveMuc);
    break;
case MucOptions.ERROR_PASSWORD_REQUIRED:
    showSnackbar(R.string.conference_requires_password, R.string.enter_password, enterPassword);
    break;
case MucOptions.ERROR_BANNED:
    showSnackbar(R.string.conference_banned, R.string.leave, leaveMuc);
    break;
case MucOptions.ERROR_MEMBERS_ONLY:
    showSnackbar(R.string.conference_members_only, R.string.leave, leaveMuc);
    break;
case MucOptions.KICKED_FROM_ROOM:
    showSnackbar(R.string.conference_kicked, R.string.join, joinMuc);
    break;