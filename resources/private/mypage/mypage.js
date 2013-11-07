;(function() {
  "use strict";

  function isNotBlank(s) { return !/^\s*$/.test(s); }
  function equals(s1, s2) { return s1 === s2; }

  function makeSaveFn(commandName, propertyNames) {
    return function(model, event) {
      var img = $(event.target).parent().find("img");
      var t = setTimeout(img.show, 200);
      ajax
        .command(commandName, _.reduce(propertyNames, function(m, n) { m[n] = model[n](); return m; }, {}))
        .success(function() { model.clear().saved(true); })
        .error(function(d) { model.clear().saved(false).error(d.text); })
        .complete(function() { clearTimeout(t); img.hide(); })
        .call();
    };
  }

  function OwnInfo() {

    var self = this;

    self.error = ko.observable();
    self.saved = ko.observable();
    self.firstName = ko.observable();
    self.lastName = ko.observable();
    self.street = ko.observable();
    self.city = ko.observable();
    self.zip = ko.observable();
    self.phone = ko.observable();
    self.role = ko.observable();
    self.architect = ko.observable();
    self.degree = ko.observable();
    self.experience = ko.observable();
    self.fise = ko.observable();
    self.qualification = ko.observable();
    self.companyName = ko.observable();
    self.companyId = ko.observable();
    self.companyStreet = ko.observable();
    self.companyZip = ko.observable();
    self.companyCity = ko.observable()
    self.attachments = ko.observable();
    self.hasAttachments = ko.computed(function() {
      var a = self.attachments();
      return a && a.length > 0;
    });
    self.loadingAttachments = ko.observable();

    self.availableQualifications = ["AA", "A", "B", "C"];

    self.init = function(u) {
      return self
        .error(null)
        .saved(false)
        .firstName(u.firstName)
        .lastName(u.lastName)
        .street(u.street)
        .city(u.city)
        .zip(u.zip)
        .phone(u.phone)
        .role(u.role)
        .architect(u.architect)
        .degree(u.degree)
        .experience(u.experience)
        .fise(u.fise)
        .qualification(u.qualification)
        .companyName(u.companyName)
        .companyId(u.companyId)
        .companyStreet(u.companyStreet)
        .companyZip(u.companyZip)
        .companyCity(u.companyCity)
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

    self.ok = ko.computed(function() { return isNotBlank(self.firstName()) && isNotBlank(self.lastName()); }, self);
    self.save = makeSaveFn("update-user",
        ["firstName", "lastName",
         "street", "city", "zip", "phone",
         "architect",
         "degree", "experience", "fise", "qualification",
         "companyName", "companyId", "companyStreet", "companyZip", "companyCity"]);

    self.updateUserName = function() {
      $("#user-name")
        .text(self.firstName() + " " + self.lastName())
        .attr("data-test-role", self.role());
      return self;
    };

    self.add = function() {
      uploadModel.init().open();
      return false;
    };

    self.fileToRemove = null;

    self.remove = function(data) {
      self.fileToRemove = data['attachment-id'];
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

    self.saved.subscribe(self.updateUserName);
  }

  function Password() {
    this.oldPassword = ko.observable("");
    this.newPassword = ko.observable("");
    this.newPassword2 = ko.observable("");
    this.error = ko.observable(null);
    this.saved = ko.observable(false);

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

    self.availableAttachmentTypes = _.map(["cv", "examination", "proficiency"], function(type) {
      return {id: type, name: loc(["userinfo.architect.attachments.name", type])};
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
      if (value === self.stateDone) ownInfo.updateAttachments();
    });

  }

  var ownInfo = new OwnInfo();
  var pw = new Password();
  var uploadModel = new UploadModel();

  hub.onPageChange("mypage", function() { ownInfo.clear(); pw.clear(); });
  hub.subscribe("login", function(e) { ownInfo.clear().init(e.user).updateUserName(); });

  $(function() {

    $("#mypage")
      .find("#own-info-form").applyBindings(ownInfo).end()
      .find("#pw-form").applyBindings(pw).end()
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
            done: function(e, data) { uploadModel.done(); },
            fail: uploadModel.error
          });

  });

})();
