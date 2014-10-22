LUPAPISTE.InviteModel = function() {
  var self = this;

  self.applicationId = null;
  self.email = ko.observable();
  self.path = ko.observable();
  self.text = ko.observable(loc('invite.default-text'));
  self.documentName = ko.observable();
  self.documentId = ko.observable();
  self.error = ko.observable();
  self.processing = ko.observable();
  self.pending = ko.observable();
  self.disabled = ko.computed(function() {
    // self.path is allowed to be empty
    return self.processing() || !util.isValidEmailAddress(self.email()) || !self.text();
  });

  self.setApplicationId = function(applicationId) {
    self.applicationId = applicationId;
  };

  self.reset = function() {
    self.email(undefined);
    self.documentName(undefined);
    self.documentId(undefined);
    self.path(undefined);
    self.text(loc('invite.default-text'));
    self.error(undefined);
  };

  self.submit = function(model) {
    self.error(undefined);
    var email = model.email();
    var text = model.text();
    var documentName = model.documentName();
    var documentId = model.documentId();
    var path = model.path();
    ajax.command("invite", { id: self.applicationId,
                             documentName: documentName,
                             documentId: documentId,
                             path: path,
                             email: email,
                             title: "uuden suunnittelijan lis\u00E4\u00E4minen",
                             text: text})
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        repository.load(self.applicationId);
        LUPAPISTE.ModalDialog.close();
      })
      .error(function(d) {
        self.error(loc(d.text));
      })
      .call();
    return false;
  };
};
