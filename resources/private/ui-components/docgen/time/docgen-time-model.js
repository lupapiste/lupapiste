LUPAPISTE.DocgenTimeModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-string-template";

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  var inputValue = self.value;

  function coerceTimeString(value) {
    if (_.isEmpty(value)) {
      return value;
    } else {
      var parts = _.reject(value.split(/\D+/), _.isEmpty);
      switch (parts.length) {
      case 0: return "0:00";
      case 1: return parts.concat("00").join(":");
      case 2: return parts.join(":");
      case 3: return parts.join(":");
      default: return _.take(parts,3).join(":") + "." + parts[3];
      }
    }
  }

  self.value = ko.pureComputed({
    read: inputValue,
    write: function(value) {
      inputValue(coerceTimeString(value));
    }
  });

};
