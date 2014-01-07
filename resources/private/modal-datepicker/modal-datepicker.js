LUPAPISTE.ModalDatepickerModel = function() {
  var self = this;
  self.dialogSelector = "#dialog-modal-datepicker";
  self.datepickerSelector = "#modal-datepicker-date";

  self.appId = 0;
  self.dateObservable = ko.observable(null);
  self.errorMessage = ko.observable(null);
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);

  self.config = null;
  self.dialogHeader = ko.observable("");
  self.dateSelectorLabel = ko.observable("");
  self.dialogHelpParagraph = ko.observable("");
  self.dialogButtonSend = ko.observable("");
  self.areYouSureMessage = ko.observable("");


  self.ok = ko.computed( function() {return self.dateObservable();} );

  self.onError = function(resp) {
    self.errorMessage(resp.text);
  };

  self.reset = function(app) {
    self.appId = app.id();
    self.dateObservable(null);
    self.errorMessage(null);
    self.processing(false);
    self.pending(false);

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
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        repository.load(self.appId);
        $("#modal-datepicker").show();
        LUPAPISTE.ModalDialog.close();
        self.errorMessage(null);
        self.dateObservable(null);
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.doCancel = function() {
    $("#modal-datepicker").show();
  };

  self.confirm = function() {
    $("#modal-datepicker").hide();
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("areyousure"),
        loc(self.config.areYouSureMessage, $(self.datepickerSelector).val()),
        {title: loc("yes"), fn: self.doSend},
        {title: loc("no"), fn: self.doCancel}
      );
  };

  //Open the dialog

  self.openWithConfig = function(config, app) {
    self.config = config;
    if (self.config) {
      self.reset(app);
      LUPAPISTE.ModalDialog.open(self.dialogSelector);
    }
  };

};
