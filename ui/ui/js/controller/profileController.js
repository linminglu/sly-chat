var ProfileController = function (model) {
    this.model = model;
    this.modal = this.createSmsVerificationModal();
};

ProfileController.prototype = {
    init : function() {
        var userInfo = this.getUserInfo();

        $("#profileAvatar").html("<a href='#'>" + createAvatar(userInfo.name, "#fff", "#212121") + "</a>");

        $("#profileNameDisplay").html(userInfo.name);

        $("#profileEmailDisplay").html(userInfo.username);

        $("#submitEmailUpdateBtn").click(this.updateEmail.bind(this));
        $("#submitUpdatePhoneBtn").click(this.requestPhoneUpdate.bind(this));
        $("#submitUpdateName").click(this.updateName.bind(this));
    },
    getUserInfo: function () {
        return {
            "username" : this.model.username,
            "phoneNumber" : this.model.phoneNumber,
            "name" : this.model.name
        }
    },
    setUserInfo: function (userInfo) {
        this.model.setUserInfo(userInfo.email, userInfo["phone-number"], userInfo.name);
    },
    clearCache : function () {
        this.model.setUserInfo('', '', '')
    },
    requestPhoneUpdate : function (e) {
        e.preventDefault();
        var formValid = validateForm("#updatePhoneForm");
        var phoneValid = validatePhone();
        var button = $("#submitUpdatePhoneBtn");

        button.prop("disabled", true);
        if(formValid == true && phoneValid == true) {
            var phone = this.getFormattedPhoneNumber();

            accountModifictationService.requestPhoneUpdate(phone).then(function (result) {
                if (result.successful === true) {
                    button.prop("disabled", false);
                    this.openSmsVerificationModal();
                }
                else {
                    console.log(result.errorMessage);
                    button.prop("disabled", false);
                }
            }.bind(this)).catch(function (e) {
                console.log("an error occured while updating phone");
                KEYTAP.exceptionController.displayDebugMessage(e);
                button.prop("disabled", false);
            });
        }
        else {
            button.prop("disabled", false);
        }
    },
    updateEmail : function (e) {
        e.preventDefault();
        if(validateForm("#updateEmailForm")) {
            var email = $("#profileEmail").val();
        }
    },
    updateName : function (e) {
        e.preventDefault();
        var button = $("#submitUpdateName");
        button.prop("disabled", true);
        if(validateForm("#updateNameForm")) {
            var name = $("#profileName").val();

            accountModifictationService.updateName(name).then(function (result) {
                if (result.successful === true) {
                    $("#profileName").val("");
                    this.model.setName(name);
                    $("#profileNameDisplay").html(name);
                    button.prop("disabled", false);
                }
                else {
                    console.log(result.errorMessage);
                    button.prop("disabled", false);
                }
            }.bind(this)).catch(function (e){
                console.log(e);
                KEYTAP.exceptionController.displayDebugMessage(e);
                button.prop("disabled", false);
            });
        }
        else {
            button.prop("disabled", false);
        }
    },
    confirmPhone : function () {
        var code = $("#smsCode").val();
        if(code != null && code != '') {
            accountModifictationService.confirmPhoneNumber(code).then(function (result) {
                if (result.successful === true) {
                    $("#phone").val("");
                    this.model.setPhoneNumber(result.accountInfo['phone-number']);
                    this.modal.close();
                }
                else {
                    console.log(result.errorMessage);
                }
            }.bind(this)).catch(function (e) {
                console.log("An error occured while confirming phone number");
                KEYTAP.exceptionController.displayDebugMessage(e);
            })
        }
    },
    getFormattedPhoneNumber : function () {
        var hiddenPhoneInput = $("#hiddenPhoneInput");

        var countryData = hiddenPhoneInput.intlTelInput("getSelectedCountryData");
        var phoneValue = $("#phone").val();

        hiddenPhoneInput.intlTelInput("setNumber", phoneValue);

        return phoneValue.indexOf(countryData.dialCode) == 0 ?
            countryData.dialCode + phoneValue.substring(countryData.dialCode.length) :
                countryData.dialCode + phoneValue;
    },
    createSmsVerificationModal: function () {
        var html = '<div class="valign-wrapper row form-wrapper" style="background-color: #fff; padding: 0; min-height: 100%;">' +
            '<div class="valign col s12" style="padding: 0;">' +
                '<div class="container" style="margin: 0; padding: 0;">' +
                    '<ul id="verification-error" style="color: red;">' +
                    '</ul>' +
                    '<h6 style="text-align: center; color: #9e9e9e;">You should receive a sms verification code shortly</h6>' +
                    '<form id="verificationForm" method="post">' +
                        '<div class="group-form col s12">' +
                            '<i class="mdi mdi-lock"></i>' +
                            '<input id="smsCode" type="text" required autocapitalize="off" placeholder="Verification code" style="border: 1px solid #eeeeee; color: #212121;">' +
                        '</div>' +
                        '<input type="hidden" id="email">' +
                    '</form>' +
                    '<button class="waves-effect waves-light btn-lg" style="width: 40%; background-color: red; margin: 10px 5px 10px 5px; padding: 10px 8px;" onclick="BootstrapDialog.closeAll();">Cancel</button>' +
                    '<button class="waves-effect waves-light btn-lg secondary-color" style="width: 40%; margin: 10px 5px 10px 5px; padding: 10px 8px;" onclick="KEYTAP.profileController.confirmPhone();">Confirm</button>' +
                    '<div style="text-align: center">' +
                        '<span>' +
                            'Didn\'t receive your verification code? <br>' +
                            '<a id="resendVerificationCode" class="secondary-color-text" href="#">Resend</a><br>' +
                        '</span>' +
                    '</div>' +
                '</div>' +
            '</div>' +
        '</div>';

        var modalContent = $(document.createElement("div"));
        modalContent.addClass("valign-wrapper");
        modalContent.addClass("row");

        var container = $(document.createElement("div"));
        container.addClass("valign");
        container.html(html);

        modalContent.append(container);

        var htmlContent = $("<div>").append(html).html();

        var bd = new BootstrapDialog();
        bd.setCssClass("statusModal whiteModal mediumModal");
        bd.setClosable(false);
        bd.setMessage(htmlContent);

        return bd;
    },
    openSmsVerificationModal : function () {
        this.modal.open();
    }
};