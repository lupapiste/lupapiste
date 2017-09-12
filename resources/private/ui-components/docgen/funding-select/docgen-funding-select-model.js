LUPAPISTE.DocgenFundingSelectModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));

  var service = params.service || lupapisteApp.services.documentDataService;

  self.selectValue = ko.observable(self.value());
  self.indicator = ko.observable().extend({notify: "always"});
  self.processing = ko.observable();
  self.pending = ko.observable();
  self.reset = ko.observable(false);

  var latestSaved = self.value();

  var doSave = function (value) {
    if( latestSaved !== value ) {
      service.updateDoc(self.documentId,
        [[params.path, value]],
        self.indicator);
      latestSaved = value;
    }
  };

  var invite = function() {
    ajax.command("invite-financial-handler",
      {id: params.applicationId,
       documentId: self.documentId,
       path: params.path})
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        repository.load(params.applicationId);
      })
      .call();
  };

  var sendNotificationToHousingOffice = function () {
    ajax.command("notify-organizations-housing-office",
      {id: params.applicationId})
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {})
      .call();
  };

  var removeInvitation = function() {
    ajax.command("remove-financial-handler-invitation",
      {id: params.applicationId})
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        repository.load(params.applicationId);
      })
      .call();
  };

  var saveFunding = function() {
    doSave(true);
    invite();
    _.delay(sendNotificationToHousingOffice, 1000);
  };

  var removeFunding = function() {
    doSave(false);
    removeInvitation();
  };

  var reset = function (value) {
    self.reset(true);
    self.selectValue(value);
    LUPAPISTE.ModalDialog.close();
  };

  self.disposedSubscribe(self.selectValue, function (value) {
    if (self.reset() === false) {
      if (value === true) {
        LUPAPISTE.ModalDialog.showDynamicYesNo(
          loc("hankkeen-kuvaus.rahoitus.add.areysure"),
          loc("hankkeen-kuvaus.rahoitus.add.help"),
          {title: loc("yes"), fn: function() {saveFunding();}},
          {title: loc("no"), fn: function() {reset(false);}}
        );
      } else {
        LUPAPISTE.ModalDialog.showDynamicYesNo(
          loc("hankkeen-kuvaus.rahoitus.remove.areysure"),
          loc("hankkeen-kuvaus.rahoitus.remove.help"),
          {title: loc("yes"), fn: function() {removeFunding();}},
          {title: loc("no"), fn: function() {reset(true);}}
        );
      }
    } else {
      self.reset(false);
    }
  });
};
