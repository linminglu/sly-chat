$(document).ready(function(){
    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");

    $(window).resize(function () {
        resizeWindow();
    });

    stateService.getState().then(function (state) {
        KEYTAP.loginController.registerForLoginEvents();

        if(state != null && typeof state.currentPage != "undefined" && state.currentPage != null) {
            if(state.currentPage.indexOf("login.html") <= -1 && state.currentPage.indexOf("register.html") <= -1) {
                $(".menu-hidden").show();
            }
            if(state.currentPage.indexOf("chat.html") > -1) {
                if (typeof state.currentContact != "undefined" && state.currentContact != null) {
                    KEYTAP.contactController.model.fetchConversationForChat(state.currentContact.email);
                }
            }else {
                KEYTAP.navigationController.smoothStateLoad(state.currentPage);
            }
        }
    }).catch(function (e) {
        KEYTAP.exceptionController.displayDebugMessage(e);
        console.log(e);
    });
});

function resizeWindow() {
    var height = window.innerHeight - 56;
    $("#main").css("height", height + "px");

    if ($("#chatContent").length) {
        document.getElementById("chatContent").contentWindow.scrollTo(0, 9999999);
    }
}
