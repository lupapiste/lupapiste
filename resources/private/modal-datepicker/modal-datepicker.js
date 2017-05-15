LUPAPISTE.ModalDatepickerModel = function() {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-modal-datepicker";
  self.datepickerSelector = "#modal-datepicker-date";

  self.appId = null;
  self.dateObservable = ko.observable(null);
  self.errorMessage = ko.observable(null);

  self.config = null;
  self.dialogHeader = ko.observable("");
  self.dateSelectorLabel = ko.observable("");
  self.dialogHelpParagraph = ko.observable("");
  self.dialogButtonSend = ko.observable("");
  self.areYouSureMessage = ko.observable("");
  self.isInvalid = ko.observable();

  self.onError = function(resp) {
    self.errorMessage(resp.text);
    LUPAPISTE.showIntegrationError("integration.title",
                                   resp.text,
                                   resp.details);
  };

  self.reset = function(app) {
    self.appId = app.id();
    self.dateObservable(null);
    self.errorMessage(null);
    self.isInvalid( true );
    self.dialogHeader(self.config.dialogHeader);
    self.dateSelectorLabel(self.config.dateSelectorLabel);
    self.dialogHelpParagraph(self.config.dialogHelpParagraph);
    self.dialogButtonSend(self.config.dialogButtonSend);
    self.areYouSureMessage(self.config.areYouSureMessage);
  };

  self.doSend = function() {
    var commandData = {id: self.appId};
    commandData[self.config.dateParameter] = $(self.datepickerSelector).val();
    if (self.config.extraParameters) {
      if (_.isFunction(self.config.extraParameters)) {
        _.merge(commandData, self.config.extraParameters());
      } else {
        _.merge(commandData, self.config.extraParameters);
      }
    }

    ajax.command(self.config.commandName, commandData)
      .success(function(resp) {
        repository.load(self.appId);
        LUPAPISTE.ModalDialog.close();
        self.errorMessage(null);
        self.dateObservable(null);
        if (self.config.checkIntegrationAvailability && !resp.integrationAvailable) {
          LUPAPISTE.ModalDialog.showDynamicOk(loc("integration.title"), loc("integration.unavailable"));
        }
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.doCancel = function() {
    LUPAPISTE.ModalDialog.close();
  };

  self.confirm = function() {
    LUPAPISTE.ModalDialog.close();
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("areyousure"),
        loc(self.config.areYouSureMessage, $(self.datepickerSelector).val()),
        {title: loc("yes"), fn: self.doSend},
        {title: loc("no"), fn: self.doCancel}
      );
  };

  //Open the dialog

  self.openWithConfig = function(config, app) {c
    self.config = config;
    if (self.config) {
      self.reset(app);
      LUPAPISTE.ModalDialog.open(self.dialogSelector);
    }
  };

  hub.subscribe("modal-datepicker", function(data) {
    console.log("Availlaan datepicker");
    var config =
      {checkIntegrationAvailability: false,
       dateParameter       : data.value,
       extraParameters     : {lang: loc.getCurrentLanguage()},
       dateSelectorLabel   : data.dateSelectorLabel,
       dialogHeader        : data.dialogHeader,
       dialogHelpParagraph : data.dialogHelpParagraph,
       dialogButtonSend    : data.dialogButtonSend,
       areYouSureMessage   : data.areYouSureMessage};
    self.config = config;
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  });

};
