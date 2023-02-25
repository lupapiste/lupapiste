;(function() {
  "use strict";

  var authorization = lupapisteApp.models.globalAuthModel;

  function makeSaveFn(commandName, propertyNames, model ) {
    return function() {

      var nullableFields = ["degree", "fiseKelpoisuus"];
      var params = _.reduce(propertyNames, function(m, n) {
        if (_.includes(nullableFields, n) && !model[n]()) {
          m[n] = "";
        } else {
          var value = model[n]();
          m[n] = _.isString(value) ? _.trim(value) : value;
        }
        return m; }, {});

      ajax
        .command(commandName, params)
        .pending(model.pending)
        .success(function(res) {
          model.clear().saved(true);
          util.showSavedIndicator(res);
          if( params.language &&
              params.language !== lupapisteApp.models.currentUser.language() &&
              params.language !== loc.getCurrentLanguage()) {
            hub.send( "change-lang", {lang: params.language});
          }
        })
        .error(function(res) {
          model.error(res.text).clear().saved(false);
          util.showSavedIndicator(res);
        })
        .call();
    };
  }

  function isPersonIdUpdateAllowed(user) {
    return user.role === "applicant"
      && _.get(user, "company.role")
      && user.personIdSource !== "identification-service"
      && user.personIdSource !== "foreign-id"; // Foreign person id formats break personId validation
  }

  function OwnInfo(uploadModel) {

    var self = this;

    var maxLength = LUPAPISTE.config.inputMaxLength;
    var reqmax = {maxLength: maxLength, required: true};

    self.error = ko.observable();
    self.saved = ko.observable(false);
    self.firstName = ko.observable().extend( reqmax );
    self.lastName = ko.observable().extend( reqmax);
    self.username = ko.observable();
    self.email = ko.observable();
    self.personId = ko.observable().extend({ personId: true });
    self.personIdEditable = ko.observable();
    self.street = ko.observable().extend( reqmax );
    self.city = ko.observable().extend( reqmax );
    self.zip = ko.observable().extend({number: true, required: true,
                                       maxLength: 5, minLength: 5});
    self.phone = ko.observable().extend({maxLength: maxLength});
    self.role = ko.observable();
    self.language = ko.observable();
    self.architect = ko.observable();
    self.degree = ko.observable().extend({ maxLength: maxLength });
    self.availableDegrees = _(LUPAPISTE.config.degrees).map(function(degree) {
      return {id: degree, name: loc(["koulutus", degree])};
    }).sortBy("name").value();
    self.graduatingYear = ko.observable().extend({ number: true, minLength: 4, maxLength: 4 });
    self.fise = ko.observable().extend({ maxLength: maxLength });
    self.fiseKelpoisuus = ko.observable();
    self.availableFiseKelpoisuusValues = _(LUPAPISTE.config.fiseKelpoisuusValues).map(function(kelp) {
      return {id: kelp, name: loc(["fisekelpoisuus", kelp])};
    }).sortBy("name").value();
    self.companyAccordionOpen = ko.observable();
    self.companyName = ko.observable().extend({ maxLength: maxLength});
    self.companyId = ko.observable().extend( { y: true });
    self.allowDirectMarketing = ko.observable();
    self.attachments = ko.observable();
    self.hasAttachments = ko.computed(function() {
      var a = self.attachments();
      return a && a.length > 0;
    });

    self.all = ko.validatedObservable([self.firstName, self.lastName, self.personId, self.street, self.city,
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
        .language(u.language)
        .email(u.email)
        .personId(u.personId)
        .personIdEditable(isPersonIdUpdateAllowed(u))
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
        .companyAccordionOpen( false )
        .companyName(u.companyName)
        .companyId(u.companyId)
        .allowDirectMarketing(u.allowDirectMarketing)
        .updateAttachments();
    };

    self.updateAttachments = function() {
      self.attachments(null);
      if (authorization.ok("user-attachments")) {
        ajax
          .query("user-attachments")
          .pending(self.loadingAttachments)
          .success(function(data) {
            self.attachments(_.map(data.attachments, function(info, id) { info.id = id; return info; }));
          })
          .call();
      }
      return self;
    };

    self.uploadDoneSubscription = hub.subscribe("user-attachments-chenged", self.updateAttachments);

    self.clear = function() {
      return self.saved(false).error(null);
    };

    self.save = makeSaveFn("update-user",
        ["firstName", "lastName",
         "street", "city", "zip", "phone",
         "language", "architect",
         "degree", "graduatingYear", "fise", "fiseKelpoisuus",
         "companyName", "companyId",
         "allowDirectMarketing"].concat(self.personIdEditable() ? ["personId"] : []),
                          self );

    self.updateUserName = function() {
      hub.send("reload-current-user");
      return self;
    };

    self.add = function() {
      uploadModel.init().open();
      return false;
    };

    self.remove = function(data) {
      hub.send( "show-dialog", {
        component: "yes-no-dialog",
        ltitle: "userinfo.architect.remove.title",
        size: "small",
        componentParams: {
          ltext: "userinfo.architect.remove.message",
          lyesTitle: "yes",
          lnoTitle: "no",
          yesFn: _.wrap( data["attachment-id"], self.doRemove )
        }
      });

      return false;
    };

    self.doRemove = function( attachmentId ) {
      ajax
        .command("remove-user-attachment", {"attachment-id": attachmentId })
        .success(function() {hub.send("user-attachments-chenged");})
        .call();
    };

    self.editUsers = function() {
      pageutil.openPage("company", self.company.id() + "/users");
    };

    self.editCompany = function() {
      pageutil.openPage("company", self.company.id());
    };

    self.companyReports = function() {
      pageutil.openPage("company-reports", self.company.id());
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

    self.save = makeSaveFn("change-passwd", ["oldPassword", "newPassword"], self);
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
        error("AJAX: UPLOAD ERROR RESULT", data.url, self.errorText);
      }
    };
    self.error = function(e, data) {
      self.state(self.stateError);
      error("AJAX: UPLOAD ERROR", data.url, data.textStatus, data.errorThrown);
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
        $input.prop("disabled", false);
      } else {
        $input.prop("disabled", true);
      }

      if (state === self.stateDone) {
        hub.send("user-attachments-chenged");
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
