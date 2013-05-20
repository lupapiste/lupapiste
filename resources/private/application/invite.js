LUPAPISTE.InviteModel = function() {
  var self = this;

  self.applicationId = null;
  self.email = ko.observable();
  self.text = ko.observable(loc('invite.default-text'));
  self.documentName = ko.observable();
  self.documentId = ko.observable();
  self.error = ko.observable();

  self.setApplicationId = function(applicationId) {
    self.applicationId = applicationId;
  };

  self.reset = function() {
    self.email(undefined);
    self.documentName(undefined);
    self.documentId(undefined);
    self.text(loc('invite.default-text'));
    self.error(undefined);
  };

  self.submit = function(model) {
    self.error(undefined);
    var email = model.email();
    var text = model.text();
    var documentName = model.documentName();
    var documentId = model.documentId();
    ajax.command("invite", { id: self.applicationId,
                             documentName: documentName,
                             documentId: documentId,
                             email: email,
                             title: "uuden suunnittelijan lis\u00E4\u00E4minen",
                             text: text})
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
