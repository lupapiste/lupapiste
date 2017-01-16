LUPAPISTE.DocgenHuoneistotTableModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(params));

  self.componentTemplate = (params.template || params.schema.template) || "default-docgen-table-template";

  self.schema = params.schema;

  self.groupId = ["table", params.documentId].concat(self.path).join("-");
  self.groupLabel = params.i18npath.concat("_group_label").join(".");
  self.groupHelp = params.schema["group-help"];

  self.authModel = params.authModel;

  self.columnHeaders = _.map(self.schema.body, function(schema) {
    return {
      name: params.i18npath.concat(schema.name),
      required: !!schema.required
    };
  });
  self.columnHeaders.push({
    name: self.groupsRemovable(self.schema) ? "remove" : "",
    required: false
  });

  function authState( state ) {
    var commands = _.get( self.schema.auth, state );
    if( _.isArray( commands) && _.size( commands )) {
      return _.some( commands, self.authModel.ok );
    }
  }

  self.disabled = self.disposedPureComputed( function() {
    var disabled = params.isDisabled
          || !(self.service.isWhitelisted( self.schema ))
          || !self.authModel.ok(self.service.getUpdateCommand(self.documentId))
          || util.getIn(params, ["model", "disabled"]);
    var authDisabled = authState( "disabled" );
    if( _.isBoolean( authDisabled ) ) {
      disabled = disabled || authDisabled;
    }
    var authEnabled = authState( "enabled" );
    if( _.isBoolean( authEnabled ) ) {
      disabled = disabled || !authEnabled;
    }
    return disabled;
  });

  self.selectOptionsTextFn = function(colName) {
    return function(item) {return loc(item.i18nkey || ["huoneistot", colName, item.name].join("."));};
  };

  function indicator(evt) {
    if (evt.type === "err") {
      hub.send("indicator", {style: "negative", message: "form.err"});
    }
  }

  function afterSave(result) {
    if (result.results) {
      self.docModel.showValidationResults(result.results);
    }
  }

  function save(model, path) {
    self.service.updateDoc(self.documentId, [[path, model()]], indicator, afterSave);
  }

  function cellInfo(doc, model, path) {
    var result = self.disposedPureComputed(function() {
      var validation = _.find(doc.validationResults(), function(validation) {
        return _.isEqual(validation.path, path);
      });
      return validation && validation.result;
    });

    var errorMessage = self.disposedPureComputed(function() {
      var errType = result() && result()[0];
      return errType && errType !== "tip" && loc(["error", result()[1]]);
    });

    var signalClasses = self.disposedPureComputed(function() {
      var classes = [];
      var res = result() ? result()[0] : undefined;
      if (res) {
        classes.push(res);
      }
      classes.push(self.size);
      return classes.join(" ");
    });

    var showMessagePanel = ko.observable(false);

    return {
      css: signalClasses,
      errorMessage: errorMessage,
      showMessagePanel: showMessagePanel,
      events: {
        mouseover: function() { showMessagePanel(true); },
        mouseout: function() { showMessagePanel(false); },
        change: function() { _.defer(save, model, path); }
      }
    };
  }

  self.rows = self.disposedPureComputed(function() {
    var doc = self.service.findDocumentById(self.documentId);
    return _.map(self.groups(), function(group) {
      return _(group.schema.body).map(function(schema) {
        var model = group.model[schema.name].model;
        return [schema.name, {model: model,
                              path: group.model[schema.name].path,
                              schema: schema,
                              info: cellInfo(doc, model, group.model[schema.name].path)}];
      }).fromPairs()
        .extend({ index: group.index,
                  schema: group.schema })
        .value();
    });
  });
};
