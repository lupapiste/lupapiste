/**
 * Component for updating server URL, username and password.
 *
 * Required parameters:
 * - server: observable that contains url, username and password from DB
 * - channel.send: function, that takes saving event as parameter
 * - readOnly: observable flag
 * - waiting: observable flag
 * - error: observable error key
 * - errorMessageTerm: observable error localization key
 * - header: localization term
 * - urlLabel: localization term
 * - saveLabel: localization term
 */
LUPAPISTE.ServerSettingsModel = function(params) {
  "use strict";

  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.readOnly = params.readOnly;
  self.waiting = params.waiting;
  self.url = ko.observable("");
  self.username = ko.observable("");
  self.password = ko.observable("");
  self.passwordPlaceholder = ko.observable("");

  self.error = params.error;
  self.errorMessageTerm = params.errorMessageTerm;

  self.header = params.header;
  self.urlLabel = params.urlLabel;
  self.saveLabel = params.saveLabel;

  self.disposedComputed( function() {
    var server = params.server();
    if(server) {
      self.url(server.url);
      self.username(server.username);
      if (server.username) {
        self.passwordPlaceholder("********");
      }
    }
  });

  self.updateServerDetails = function() {
    var username = _.trim(self.username());
    params.channel.send( {
      url: _.trim(self.url()),
      username: username,
      password: self.password()
    });
    if (!username) {
      self.passwordPlaceholder("");
    }
  };
};
