;(function() {
  "use strict";

  var authorization = lupapisteApp.models.globalAuthModel;

  function makeSaveFn(commandName, propertyNames) {
    return function(model, event) {
      if (!event) {
        return;
      }

      var img = $(event.target).parent().find("img");
      var t = setTimeout(img.show, 200);
      var params = _.reduce(propertyNames, function(m, n) {
        if (n === "degree" && !model[n]()) {
          m[n] = "";
        } else {
          m[n] = model[n]();
        }
        return m; }, {});

      ajax
        .command(commandName, params)
        .pending(model.pending)
        .success(function(res) {
          model.clear().saved(true);
          util.showSavedIndicator(res);
        })
        .error(function(res) {
          model.error(res.text).clear().saved(false);
          util.showSavedIndicator(res);
        })
        .complete(function() { clearTimeout(t); img.hide(); })
        .call();
    };
  }

  function OwnInfo(uploadModel) {

    var self = this;

    self.error = ko.observable();
    self.saved = ko.observable(false);
    self.firstName = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.lastName = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.username = ko.observable();
    self.email = ko.observable();
    self.street = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.city = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.zip = ko.observable().extend({number: true, maxLength: 5, minLength: 5});
    self.phone = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.role = ko.observable();
    self.architect = ko.observable();
    self.degree = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.availableDegrees = _(LUPAPISTE.config.degrees).map(function(degree) {
      return {id: degree, name: loc(["koulutus", degree])};
    }).sortBy("name").value();
    self.graduatingYear = ko.observable().extend({ number: true, minLength: 4, maxLength: 4 });
    self.fise = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.fiseKelpoisuus = ko.observable();
    self.availableFiseKelpoisuusValues = _(LUPAPISTE.config.fiseKelpoisuusValues).map(function(kelp) {
      return {id: kelp, name: loc(["fisekelpoisuus", kelp])};
    }).sortBy("name").value();
    self.companyName = ko.observable().extend({ maxLength: LUPAPISTE.config.inputMaxLength });
    self.companyId = ko.observable().extend( { y: true });
    self.allowDirectMarketing = ko.observable();
    self.attachments = ko.observable();
    self.hasAttachments = ko.computed(function() {
      var a = self.attachments();
      return a && a.length > 0;
    });

    self.all = ko.validatedObservable([self.firstName, self.lastName, self.street, self.city,
                                       self.zip, self.phone, self.degree, self.graduatingYear, self.fise, self.fiseKelpoisuus,
                                       self.companyName, self.companyId]);

    self.isValid = ko.computed(function() {
      return self.all.isValid();
    });

    self.pending = ko.observable();

    self.processing = ko.observable();

    self.loadingAttachments = ko.observable();

    self.company = {
      id:    ko.observable(),
      name:  ko.observable(),
      y:     ko.observable(),
      document: ko.observable()
    };

    self.authority = ko.computed(function() { return self.role() === "authority"; });

    self.companyShow = ko.observable();
    self.showSimpleCompanyInfo = ko.computed(function () { return !self.companyShow(); });
    self.companyLoading = ko.observable();
    self.companyLoaded = ko.computed(function() { return !self.companyLoading(); });

    self.init = function(u) {
      self.company
        .id(null)
        .name(null)
        .y(null);
      if (u.company.id) {
        self
          .companyShow(true)
          .companyLoading(true);
        ajax
          .query("company", {company: u.company.id})
          .pending(self.companyLoading)
          .success(function(data) {
            ko.mapping.fromJS(data.company, {}, self.company);
          })
          .call();
      } else {
        self
          .companyShow(false)
          .companyLoading(false);
      }
      return self
        .error(null)
        .saved(false)
        .firstName(u.firstName)
        .lastName(u.lastName)
        .username(u.username)
        .email(u.email)
        .street(u.street)
        .city(u.city)
        .zip(u.zip)
        .phone(u.phone)
        .role(u.role)
        .architect(u.architect)
        .degree(u.degree)
        .graduatingYear(u.graduatingYear)
        .fise(u.fise)
        .fiseKelpoisuus(u.fiseKelpoisuus)
        .companyName(u.companyName)
        .companyId(u.companyId)
        .allowDirectMarketing(u.allowDirectMarketing)
        .updateAttachments();
    };

    self.updateAttachments = function() {
      self.attachments(null);
      ajax
        .query("user-attachments", {})
        .pending(self.loadingAttachments)
        .success(function(data) {
          self.attachments(_.map(data.attachments, function(info, id) { info.id = id; return info; }));
        })
        .call();
      return self;
    };

    self.uploadDoneSubscription = hub.subscribe("MyPage::UploadDone", self.updateAttachments);

    self.clear = function() {
      return self.saved(false).error(null);
    };

    self.save = makeSaveFn("update-user",
        ["firstName", "lastName",
         "street", "city", "zip", "phone",
         "architect",
         "degree", "graduatingYear", "fise", "fiseKelpoisuus",
         "companyName", "companyId",
         "allowDirectMarketing"]);

    self.updateUserName = function() {
      hub.send("reload-current-user");
      return self;
    };

    self.add = function() {
      uploadModel.init().open();
      return false;
    };

    self.fileToRemove = null;

    self.remove = function(data) {
      self.fileToRemove = data["attachment-id"];
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("userinfo.architect.remove.title"),
        loc("userinfo.architect.remove.message"),
        {title: loc("yes"), fn: self.doRemove},
        {title: loc("no")}
      );
      return false;
    };

    self.doRemove = function() {
      ajax
        .command("remove-user-attachment", {"attachment-id": self.fileToRemove})
        .success(self.updateAttachments)
        .call();
    };

    self.editUsers = function() {
      pageutil.openPage("company", self.company.id() + "/users/");
    };

    self.editCompany = function() {
      pageutil.openPage("company", self.company.id());
    };

    self.saved.subscribe(self.updateUserName);

    self.disable = ko.pureComputed(function() {
      return !authorization.ok("update-user") || self.processing();
    });

    self.dispose = function() {
      hub.unsubscribe(self.uploadDoneSubscription);
    };
  }

  function Password() {
    var self = this;
    self.newPassword = ko.observable("");
    self.newPassword2 = ko.observable("").extend({match: {params: self.newPassword, message: loc("mypage.noMatch")}});
    self.error = ko.observable(null);
    self.saved = ko.observable(false);
    self.pending = ko.observable(false);
    self.processing = ko.observable(false);

    // old pasword field will show error if change password request fails
    self.oldPassword = ko.observable("").extend({
      validation: {
        validator: function (val, error) {
          return !Boolean(error);
        },
        message: loc("mypage.old-password-does-not-match"),
        params: self.error
      }
    });

    // set error null when old password field is altered
    ko.computed(function() {
      self.oldPassword(); // just to trigger computed
      self.error(null);
    });

    self.clear = function() {
      if (self.error()) {
        return self;
      }
      return self
        .oldPassword("")
        .newPassword("")
        .newPassword2("")
        .saved(false)
        .error(null);
    };

    self.disable = ko.pureComputed(function() {
      return !authorization.ok("change-passwd") || self.processing();
    });

    self.ok = ko.pureComputed(function() {
      return !self.disable() && !_.isBlank(self.oldPassword()) && util.isValidPassword(self.newPassword()) && self.newPassword() === self.newPassword2(); });

    self.noMatch = ko.computed(function() {
      return !_.isBlank(self.newPassword()) && !_.isBlank(self.newPassword2()) && self.newPassword() !== self.newPassword2();
    });

    self.save = makeSaveFn("change-passwd", ["oldPassword", "newPassword"]);
    self.quality = ko.pureComputed(function() {
      return util.getPwQuality(self.newPassword());
    });
  }

  function UploadModel() {
    var self = this;

    self.stateInit     = 0;
    self.stateReady    = 1;
    self.stateSending  = 2;
    self.stateDone     = 3;
    self.stateError    = 4;
    self.errorText     = ko.observable("");

    self.state = ko.observable(-1); // -1 makes sure that init() fires state change.

    self.ready = _.partial(self.state, self.stateReady);
    self.sending = _.partial(self.state, self.stateSending);
    self.done = function(data) {
      if (data.result.ok) {
        self.state(self.stateDone);
        LUPAPISTE.ModalDialog.close();
      } else {
        self.state(self.stateReady);
        self.errorText(data.result.text);
      }
    };
    self.error = function(e, data) {
      self.state(self.stateError);
      error("AJAX: ERROR", data.url, data.result);
    };

    self.start = ko.observable();
    self.filename = ko.observable();
    self.filesize = ko.observable();
    self.fileId = ko.observable();
    self.prop = ko.observable();
    self.attachmentType = ko.observable();
    self.csrf = ko.observable();
    self.canStart = ko.computed(function() {
      return !_.isBlank(self.filename()) && self.attachmentType();
    });

    self.availableAttachmentTypes = _.map(LUPAPISTE.config.userAttachmentTypes, function(type) {
      return {id: type, name: loc(["attachmentType", type])};
    });

    self.init = function() {
      return self
        .state(self.stateInit)
        .filename(null)
        .filesize(null)
        .start(null)
        .attachmentType(null)
        .csrf($.cookie("anti-csrf-token"))
        .errorText("");
    };

    self.open = function() {
      LUPAPISTE.ModalDialog.open("#dialog-userinfo-architect-upload");
      return self;
    };

    self.upload = function() {
      self.start()();
      return false;
    };

    ko.computed(function() {
      var state = self.state();

      // jQuery upload-plugin replaces the input element after each file selection and
      // doing so it loses all listeners. This keeps input up-to-date with 'state':
      var $input = $("#dialog-userinfo-architect-upload input[type=file]");
      if (state < self.stateSending) {
        $input.removeAttr("disabled");
      } else {
        $input.attr("disabled", "disabled");
      }

      if (state === self.stateDone) {
        hub.send("MyPage::UploadDone");
      }
    });
  }

  var uploadModel = new UploadModel();
  var ownInfo = new OwnInfo(uploadModel);
  var pw = new Password();

  hub.onPageLoad("mypage", function() { ownInfo.clear(); pw.clear(); });
  hub.subscribe("login", function(e) { ownInfo.clear().init(e.user); });

  $(function() {

    $("#mypage")
      .find("#own-info-form").applyBindings(ownInfo).end()
      .find("#pw-form").applyBindings(pw).end()
      .find("#mypage-change-email").applyBindings({userinfo: ownInfo, authorization: authorization}).end()
      .find("#mypage-register-company").applyBindings(ownInfo).end()
      .find("#mypage-company").applyBindings(ownInfo).end()
      .find("#dialog-userinfo-architect-upload")
        .applyBindings(uploadModel)
        .find("form")
          .fileupload({
            url: "/api/upload/user-attachment",
            type: "POST",
            dataType: "json",
            replaceFileInput: true,
            autoUpload: false,
            add: function(e, data) {
              var f = data.files[0];
              uploadModel
                .start(function() { data.process().done(function() { data.submit(); }); })
                .filename(f.name)
                .filesize(f.size);
            },
            send: uploadModel.sending,
            done: function(e, data) {
              uploadModel.done(data);
            },
            fail: uploadModel.error
          });

  });

})();
