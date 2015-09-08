;(function() {
  "use strict";

  function isNotBlank(s) { return !/^\s*$/.test(s); }
  function equals(s1, s2) { return s1 === s2; }

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
        .success(function() {
          model.clear().saved(true);
          hub.send("indicator", {style: "positive"}); })
        .error(function(d) {
          model.clear().saved(false).error(d.text);
          hub.send("indicator", {style: "negative"});
        })
        .complete(function() { clearTimeout(t); img.hide(); })
        .call();
    };
  }

  function OwnInfo() {

    var self = this;

    self.error = ko.observable();
    self.saved = ko.observable(false);
    self.firstName = ko.observable().extend({ maxLength: 255 });
    self.lastName = ko.observable().extend({ maxLength: 255 });
    self.username = ko.observable();
    self.street = ko.observable().extend({ maxLength: 255 });
    self.city = ko.observable().extend({ maxLength: 255 });
    self.zip = ko.observable().extend({number: true, maxLength: 5, minLength: 5});
    self.phone = ko.observable().extend({ maxLength: 255 });
    self.role = ko.observable();
    self.architect = ko.observable();
    self.degree = ko.observable().extend({ maxLength: 255 });
    self.availableDegrees = _(LUPAPISTE.config.degrees).map(function(degree) {
      return {id: degree, name: loc(["koulutus", degree])};
    }).sortBy("name").value();
    self.graduatingYear = ko.observable().extend({ number: true, minLength: 4, maxLength: 4 });
    self.fise = ko.observable().extend({ maxLength: 255 });
    self.companyName = ko.observable().extend({ maxLength: 255 });
    self.companyId = ko.observable().extend( { y: true });
    self.allowDirectMarketing = ko.observable();
    self.attachments = ko.observable();
    self.hasAttachments = ko.computed(function() {
      var a = self.attachments();
      return a && a.length > 0;
    });

    self.all = ko.validatedObservable([self.firstName, self.lastName, self.street, self.city,
                                       self.zip, self.phone, self.degree, self.graduatingYear, self.fise,
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
        .street(u.street)
        .city(u.city)
        .zip(u.zip)
        .phone(u.phone)
        .role(u.role)
        .architect(u.architect)
        .degree(u.degree)
        .graduatingYear(u.graduatingYear)
        .fise(u.fise)
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

    self.clear = function() {
      return self.saved(false).error(null);
    };

    self.save = makeSaveFn("update-user",
        ["firstName", "lastName",
         "street", "city", "zip", "phone",
         "architect",
         "degree", "graduatingYear", "fise",
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
  }

  function Password() {
    this.oldPassword = ko.observable("");
    this.newPassword = ko.observable("");
    this.newPassword2 = ko.observable("").extend({match: {params: this.newPassword, message: loc("mypage.noMatch")}});
    this.error = ko.observable(null);
    this.saved = ko.observable(false);
    this.pending = ko.observable();
    this.processing = ko.observable();

    this.clear = function() {
      return this
        .oldPassword("")
        .newPassword("")
        .newPassword2("")
        .saved(false)
        .error(null);
    };

    this.ok = ko.computed(function() { return isNotBlank(this.oldPassword()) && util.isValidPassword(this.newPassword()) && equals(this.newPassword(), this.newPassword2()); }, this);
    this.noMatch = ko.computed(function() { return isNotBlank(this.newPassword()) && isNotBlank(this.newPassword2()) && !equals(this.newPassword(), this.newPassword2()); }, this);

    this.save = makeSaveFn("change-passwd", ["oldPassword", "newPassword"]);
    this.quality = ko.computed(function() { return util.getPwQuality(this.newPassword()); }, this);
  }

  function UploadModel() {
    var self = this;

    self.stateInit     = 0;
    self.stateReady    = 1;
    self.stateSending  = 2;
    self.stateDone     = 3;
    self.stateError    = 4;

    self.state = ko.observable(-1); // -1 makes sure that init() fires state change.

    self.ready = _.partial(self.state, self.stateReady);
    self.sending = _.partial(self.state, self.stateSending);
    self.done = _.partial(self.state, self.stateDone);
    self.error = _.partial(self.state, self.stateError);

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
        .csrf($.cookie("anti-csrf-token"));
    };

    self.open = function() {
      LUPAPISTE.ModalDialog.open("#dialog-userinfo-architect-upload");
      return self;
    };

    self.upload = function() {
      self.start()();
      return false;
    };

    // jQuery upload-plugin replaces the input element after each file selection and
    // doing so it loses all listeners. This keeps input up-to-date with 'state':
    self.state.subscribe(function(value) {
      var $input = $("#dialog-userinfo-architect-upload input[type=file]");
      if (value < self.stateSending) {
        $input.removeAttr("disabled");
      } else {
        $input.attr("disabled", "disabled");
      }
    });

    self.state.subscribe(function(value) {
      if (value === self.stateDone) {
        ownInfo.updateAttachments();
      }
    });

  }

  var ownInfo = new OwnInfo();
  var pw = new Password();
  var uploadModel = new UploadModel();

  hub.onPageLoad("mypage", function() { ownInfo.clear(); pw.clear(); });
  hub.subscribe("login", function(e) { ownInfo.clear().init(e.user); });

  $(function() {

    $("#mypage")
      .find("#own-info-form").applyBindings(ownInfo).end()
      .find("#pw-form").applyBindings(pw).end()
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
            done: function() {
              uploadModel.done();
              LUPAPISTE.ModalDialog.close();
            },
            fail: uploadModel.error
          });

  });

})();
