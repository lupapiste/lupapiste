LUPAPISTE.DocgenHuoneistotTableModel = function(params) {
  "use strict";
  var self = this;

  var LISAYS = "lisäys";
  var MUUTOS = "muutos";
  var POISTO = "poisto";

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(params));

  self.componentTemplate = (params.template || params.schema.template) || "default-docgen-table-template";

  self.schema = params.schema;
  self.model = params.model;

  self.groupId = ["table", params.documentId].concat(self.path).join("-");
  self.groupLabel = params.i18npath.concat("_group_label").join(".");
  self.groupHelp = params.schema["group-help"];

  self.authModel = params.authModel;

  var appAuth = lupapisteApp.models.applicationAuthModel.ok;
  self.isChangePermit = appAuth( "change-permit-premises-note" );
  self.highlightChanges = self.isChangePermit
    || _.includes( ["rakennuksen-muuttaminen", "rakennuksen-laajentaminen"],
                   _.get( params, "docModel.schema.info.name"));

  var columnOrder = ["huoneistoTyyppi", "porras",
                     "huoneistonumero", "jakokirjain", "huoneluku",
                     "keittionTyyppi", "huoneistoala", "WCKytkin",
                     "ammeTaiSuihkuKytkin", "saunaKytkin",
                     "parvekeTaiTerassiKytkin", "lamminvesiKytkin", "pysyvaHuoneistotunnus"];

  self.columnName = function( schema ) {
    return params.i18npath.concat(schema.name);
  };

  self.columnHeaders = _(self.schema.body)
    .filter( function( schema ) {
      //We won't show muutostapa anymore.
      //Mark to be removed and highlight changes functionality covers the explicit muutostapa select
      return !schema.hidden && schema.name !== "muutostapa";
    })
    .sortBy( function ( schema ) {
      return _.indexOf( columnOrder, schema.name );
    })
    .map( function(schema) {
      return {
        name: self.columnName( schema ),
        required: Boolean(schema.required),
        readonly: Boolean(schema.readonly)
      };
    })
    .value();

  self.columnCount = _.size( self.columnHeaders )
    + (self.isChangePermit ? 1 :0 );

  self.premiseHasSource = function(premise) {
    return Boolean(_.find(premise, "source"));
  };

  if( self.isChangePermit ) {
    _.forEach( self.groups(), function( group, index ) {
      var premise = self.model[index];
      var model = group.model.muutostapa.model;
      var sourceValue = _.get( premise, "muutostapa.sourceValue" );
      var currentValue = model();
      var hasSource = self.premiseHasSource( premise );

      if( currentValue === LISAYS && hasSource && currentValue !== sourceValue ) {
        model( sourceValue );
      }
    });
  }

  self.columnHeaders.push({
    name: self.groupsRemovable(self.schema, true) ? "huoneistotTable.actions" : "",
    required: false,
    readonly: false
  });

  self.showChangesOnly = ko.observable(false);

  function authState( state ) {
    var commands = _.get( self.schema.auth, state );
    if( _.isArray( commands) && _.size( commands )) {
      return _.some( commands, self.authModel.ok );
    }
  }

  self.disabled = self.disposedPureComputed( function() {
    var disabled = params.isDisabled
        || self.service.isListDisabled( self.schema )
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

  function afterSave( result) {
    if( result.results) {
      self.docModel.showValidationResults(result.results);
    }
  }

  var updatesToSend = ko.observableArray( [] );

  var sendUpdates = _.debounce( function()  {
    var updates = updatesToSend.removeAll();
    if( _.size( updates ) ) {
      self.service.updateDoc( self.documentId, updates, indicator, afterSave );
    }
  }, 200 );

  function doSave( model, path ) {
    updatesToSend.push( [path, model()] );
    sendUpdates();
  }

  function save( model, path ) {
    // Deferring is needed in order to (hopefully) make sure that the
    // model observable has been updated before the actual save.
    _.defer( doSave, model, path );
  }

  // Get the actual document values, bypassing the row model
  function getRawData(premiseIndex, propertyKey, path) {
    var data = {};
    if (!_.isNil(premiseIndex) && propertyKey) {
      var premise = _.get(self.model, [premiseIndex]);
      if (premise) {
        var property = premise[propertyKey];
        data = _.pick(property, path);
      }
    }
    return data;
  }

  function getSourceData(premiseIndex, propertyKey) {
    return getRawData(premiseIndex, propertyKey, ["source", "sourceValue"]);
  }

  // changeType is either edited, reverted or null (not edited)
  function resolveRegularMuutostapa( currentValue, sourceValue, isNewRow, changeType ) {
    var isEdited = changeType === "edited";
    var isReverted = changeType === "reverted";
    var changedValue = currentValue !== sourceValue;

    if( isNewRow ) {
      return LISAYS;
    }

    if( isEdited ) {
      return MUUTOS;
    }

    if( !isEdited && !isReverted) {
      return  _.includes( [POISTO, LISAYS], sourceValue ) ? null : sourceValue ;
    }

    if( isReverted && changedValue ) {
      return sourceValue === LISAYS ? null : sourceValue;
    }

    if( isReverted ) {
      return null;
    }

    // All the other scenarios
    return currentValue;
  }

  function resolveChangePermitMuutostapa( currentValue, sourceValue, isNewRow, changeType ) {
    var isEdited = changeType === "edited";
    var isReverted = changeType === "reverted";

    if( isNewRow ) {
      return LISAYS;
    }

    if( isEdited ) {
      return sourceValue === LISAYS ? LISAYS : MUUTOS;
    }

    if( isReverted) {
      return sourceValue === POISTO ? MUUTOS : sourceValue;
    }

    return sourceValue;
  }

  function resolveMuutostapa( currentValue, sourceValue, isNewRow, changeType ) {
    var fun = (self.isChangePermit ? resolveChangePermitMuutostapa : resolveRegularMuutostapa);
    return fun( currentValue, sourceValue, isNewRow, changeType );
  }

  function nilToEmptyString(x) {
    //Note that x can be false
    return _.isNil(x) ? "" : x;
  }

  function cellInfo(doc, model, path, cellType, isNewRow, readOnly) {
    var premiseIndex = path[1];
    var propertyKey = path[2];
    var sourceData = getSourceData(premiseIndex, propertyKey);

    var validationResult = self.disposedPureComputed(function() {
      var validation = _.find(doc.validationResults(), function(validation) {
        return _.isEqual(validation.path, path);
      });
      return validation;
    });

    var isEditedCell = self.disposedPureComputed(function() {
      var currentValue = model();
      var sourceValue = sourceData.sourceValue;
      var isEdited =
          nilToEmptyString(currentValue) !== nilToEmptyString(sourceValue) //Different value

      // Not edited when BOTH OFF
          && !(_.isNil(currentValue) && (sourceValue === false)) //Not edited when currentValue not set and source = false. I.e. boolean not switched ON
          && !(currentValue === false && _.isNil(sourceValue));  //not edited when current value switched off and no source value.
      return !readOnly && !isNewRow() && isEdited;
    });

    function updateCell(newValue) {
      model(newValue);
      save( model, path);
    }

    function revertMuutostapa() {
      updateCell( resolveMuutostapa( model(),
                                     sourceData.sourceValue,
                                     isNewRow(),
                                     "reverted" ));
    }

    function isMuutostapa() {
      return propertyKey === "muutostapa";
    }

    function toEmptyValue(fieldType) {
      var emptyValuesPerType = {"string": "",
                                "checkbox": false};
      var emptyValForFieldType = emptyValuesPerType[fieldType];
      return _.isNil(emptyValForFieldType) ? "" : emptyValForFieldType;
    }

    function revertCell() {
      if (readOnly) {
        return;
      }
      if (isMuutostapa()) {
        revertMuutostapa();
        return;
      }
      var currentValue = nilToEmptyString(model());
      var sourceValue = sourceData.sourceValue || toEmptyValue(cellType);

      if (sourceValue !== currentValue) {
        updateCell(sourceValue);
      }
    }

    var errorMessageId = self.disposedPureComputed(function() {
      var result = _.get( validationResult(), "result" );
      var errType = _.get( result, "0" );
      if( errType  && errType  !== "tip"  && loc(["error", result[1]]) ) {
        return docutils.warningId( validationResult() );
      }

    });

    var signalClasses = self.disposedPureComputed(function() {
      var classes = [];
      var res = _.get( validationResult(), "result.0" );
      if (res) {
        classes.push(res);
      }
      classes.push(self.size);
      return classes.join(" ");
    });

    var allClasses = self.disposedPureComputed(function() {
      var editedClass = isEditedCell() ? "edited-cell" : "";
      var allClasses = signalClasses() + " " + editedClass;
      return allClasses;
    });


    function change() {
      save( model, path );
      if( !isMuutostapa() ) {
        self.updateMuutostapa( premiseIndex );
      }
    }

    function isSelect(propertyKey) {
      return _.includes(["keittionTyyppi", "huoneistoTyyppi"], propertyKey);
    }

    var tooltip = self.disposedPureComputed(function() {
      if (!isEditedCell()) {
        return "";
      }
      var originalValue = nilToEmptyString(sourceData.sourceValue);
      if (isSelect(propertyKey) && originalValue) {
        originalValue = self.selectOptionsTextFn(propertyKey)({"name": originalValue});
      }
      return loc("huoneistotTable.cellTooltipText") + ": " + originalValue;
    });

    return {
      css: allClasses,
      errorMessageId: errorMessageId,
      isEditedCell: isEditedCell,
      tooltip: tooltip,
      revertCell: revertCell,
      updateCell: updateCell,
      source: sourceData,
      events: {
        change: change
      }
    };
  }

  function updateChangePermitMuutostapa( premiseIndex ) {
    var row = self.getRowWithIndex(self.rows(), premiseIndex);
    var premise = _.get(self.model, [premiseIndex]);
    var model = row.muutostapa.model;
    var path = row.muutostapa.path;
    var sourceValue = _.get( premise, "muutostapa.sourceValue" );
    var currentValue = model();

    var resolved = resolveMuutostapa( currentValue,
                                      sourceValue,
                                      row.isNewRow(),
                                      row.isEditedRow() ? "edited" : null );
    if( resolved !== model() ) {
      model( resolved );
      save( model, path );
    }
  }

  var changeFlags = {};

  function initChangeFlag( index ) {
    if( !changeFlags[index]) {
      changeFlags[index] = ko.observable( getRawData( index, "muutostapa", ["value"])
                                          .value);
    }
  }

  self.rows = self.disposedPureComputed(function() {
    var doc = self.service.findDocumentById(self.documentId);
    var premiseRows = _(self.groups()).map(function(group) {

      var isNewRow = self.disposedPureComputed(function() {
        var premiseIndex = group.index;
        var premise = _.get(self.model, [premiseIndex]);
        return !self.premiseHasSource(premise);
      });

      var rows =  _(group.schema.body).map(function(schema) {
        var model = group.model[schema.name].model;
        return [schema.name, {model: model,
                              path: group.model[schema.name].path,
                              schema: schema,
                              readonly: schema.readonly,
                              info: cellInfo(doc,
                                             model,
                                             group.model[schema.name].path,
                                             schema.type,
                                             isNewRow,
                                             schema.readonly)}];
      }).fromPairs()
          .extend({index: group.index,
                   schema: group.schema,
                   path: [group.schema.name, group.index],
                   isNewRow: isNewRow})
          .value();
      return rows;
    }).map(function(row) {
      row.warnings = self.disposedPureComputed( function() {
        return _.sortBy(docutils.updateWarnings( doc, row.path, doc.validationResults() ),
                        function ( warn ) {
                          return _.indexOf( columnOrder, _.last( warn.path ) );
                        });
      });
      row.isEditedRow = self.disposedPureComputed(function() {
        var rowWithoutMuutostapa = _.omit(row, "muutostapa");
        return !!_.find(_.values(rowWithoutMuutostapa), function(rowValues) {
          var isEditedCell = _.get(rowValues, "info.isEditedCell");
          return !row.isNewRow() && isEditedCell && isEditedCell();
        });
      });
      row.showInChanges = self.disposedPureComputed( function() {
        if( self.isChangePermit ) {
          var sdata = getSourceData( row.index, "muutostapa" );
          return Boolean( sdata.source && sdata.sourceValue);
        }
      });

      if( row.showInChanges() ) {
        initChangeFlag( row.index );
        var flagObs = changeFlags[row.index];

        row.inChanges = self.disposedComputed( {
          read: function() {
            return flagObs();
          },
          write: function( flag ) {
            var sdata = getSourceData( row.index, "muutostapa" );
            var sourceValue = sdata.source && sdata.sourceValue;
            row.muutostapa.model( flag ? sourceValue : null );
            save(row.muutostapa.model, row.muutostapa.path);
            flagObs( flag );
          }
        });
      } else {
        row.inChanges = _.noop;
      }
      row.notInChanges = self.disposedPureComputed( function() {
        return row.showInChanges() && !row.inChanges();
      });
      row.isToBeRemoved = self.disposedPureComputed(function() {
        return row.muutostapa.model() === POISTO;
      });
      row.fieldsDisabled = self.disposedPureComputed( function() {
        return self.disabled() || row.isToBeRemoved() || row.notInChanges();

      });
      row.markAsRemoved = function() {
        row.muutostapa.model(POISTO);
        save(row.muutostapa.model, row.muutostapa.path);
      };
      row.revertChanges = self.disposedPureComputed(function() {
        _.map(row, function(values) {
          var revertFn = _.get( values, "info.revertCell", _.noop);
          revertFn();
        });
      });
      //is used to check if this row is unchanged in relation to base permit or existing building
      //new, edited and removed rows are considered to differ from original
      row.differsFromOriginal = function() {
        return row.isNewRow() || row.isEditedRow() || row.isToBeRemoved();
      };

      row.rowCss = self.disposedPureComputed( function() {
        var highlight = self.highlightChanges;
        var mt = row.muutostapa.model();
        var css = {"removed-row": false,
                   "new-row": false,
                   "edited-row": false};

        if( row.notInChanges() ) {
          return css;
        }

        if( (mt === POISTO || row.isToBeRemoved()) && highlight ) {
          return _.set( css, "removed-row", true );
        }

        if((mt === LISAYS || row.isNewRow()) && highlight) {
          return _.set( css, "new-row", true );
        }

        if((mt === MUUTOS || row.isEditedRow()) && highlight ) {
          return _.set( css, "edited-row", true );
        }

        return css;
      });

      row.isVisible = self.disposedPureComputed( function() {
        return !(self.showChangesOnly() && !row.differsFromOriginal());
      });

      return row;
    }).value();

    return premiseRows;
  });

  self.allInChanges = self.disposedComputed({
    read: function() {
      return !_.some( self.rows(),
                      function( row ) {
                        return row.notInChanges();
                      });
    },
    write: function( flag ) {
      _.forEach( self.rows(),
                 function( row ) {
                   if( row.showInChanges()
                       && row.inChanges() !== flag ) {
                     row.inChanges( flag );
                   }
                 });
    }
  });

  self.numberOfDifferingRowsToOriginal = self.disposedPureComputed(function() {
    return _(self.rows())
      .filter(function(row) {return row.differsFromOriginal();})
      .value()
      .length;
  });

  self.showChangesOnlyLabel = self.disposedPureComputed( function() {
    return loc( "huoneistot.showChangesOnly", self.numberOfDifferingRowsToOriginal() );
  });

  self.getRowWithIndex = function(rows, index) {
    return _.find(rows, {index: index});
  };

  function updateRegularMuutostapa(premiseIndex) {
    var row = self.getRowWithIndex(self.rows(), premiseIndex);
    var premise = _.get(self.model, [premiseIndex]);
    var model = row.muutostapa.model;
    var path = row.muutostapa.path;
    var currentValue = model();
    var sourceValue = _.get( premise, "muutostapa.sourceValue");

    var resolved = resolveMuutostapa( currentValue,
                                      sourceValue,
                                      row.isNewRow(),
                                      row.isEditedRow() ? "edited" : null );
    if( resolved !== currentValue ) {
      model( resolved );
      save( model, path );
    }
  }

  self.updateMuutostapa = function( premiseIndex ) {
    var row = self.getRowWithIndex(self.rows(), premiseIndex);
    if( self.isChangePermit && !row.isNewRow()) {
      updateChangePermitMuutostapa( premiseIndex );
    } else {
      updateRegularMuutostapa( premiseIndex );
    }
  };

  self.showMuutostapa = self.disposedComputed( function() {
    return !_.get( _.first( self.rows()), "muutostapa.schema.hidden");
  });

  self.tableHelp = self.disposedComputed( function() {
    var help = loc( "huoneistotTable.help" );
    return self.showMuutostapa()
      ? loc( "huoneistotTable.help.muutostapa" ) + help
      : help;
  });

  self.showPremisesUpload = self.disposedComputed( function() {
    var doc = self.service.findDocumentById(self.documentId);
    var documentName = util.getIn( doc, ["schema", "name"]);
    var isNewBuilding = documentName === "uusiRakennus";

    //Changes are highlighted for change permits and extension permits
    //We dont't want excel upload for those since it replaces all previous data
    //Excel import is suitable ONLY for buildings with no previous premises
    var hasPreviousPremises = self.highlightChanges;

    return isNewBuilding && !hasPreviousPremises;
  });

  self.premisesExcelDownloadUrl = function (applicationId) {
    return sprintf("/api/raw/download-premises-template?%s&%s&%s",
                   "document-id=" + self.documentId,
                   "application-id=" + applicationId,
                   "lang=" + loc.getCurrentLanguage());
  };

  self.revertChanges = function(group) {
    var row = self.rows()[group.index];
    row.revertChanges();
  };

  self.markAsRemoved = function(group) {
    var row = self.rows()[group.index];
    row.markAsRemoved();
  };

  self.addNewRow = function() {
    self.addGroupWith({"muutostapa": {"value": LISAYS}});
  };

  self.copyLastRow = function() {
    self.duplicateLastGroupWith({"muutostapa": {"value": LISAYS}});
  };

  self.tableIsEmpty = self.disposedPureComputed( function() {
    return _.isEmpty(self.rows());
  });

};
