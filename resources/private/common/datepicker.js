/*global jQuery:false */

jQuery(function($){
  "use strict";

  $.datepicker.regional.fi = {
      closeText: 'Sulje',
      prevText: '&laquo;Edellinen',
      nextText: 'Seuraava&raquo;',
      currentText: 'T&auml;n&auml;&auml;n',
      monthNames: ['Tammikuu','Helmikuu','Maaliskuu','Huhtikuu','Toukokuu','Kes&auml;kuu','Hein&auml;kuu','Elokuu','Syyskuu','Lokakuu','Marraskuu','Joulukuu'],
      monthNamesShort: ['Tammi','Helmi','Maalis','Huhti','Touko','Kes&auml;','Hein&auml;','Elo','Syys','Loka','Marras','Joulu'],
      dayNamesShort: ['Su','Ma','Ti','Ke','To','Pe','Su'],
      dayNames: ['Sunnuntai','Maanantai','Tiistai','Keskiviikko','Torstai','Perjantai','Lauantai'],
      dayNamesMin: ['Su','Ma','Ti','Ke','To','Pe','La'],
      weekHeader: 'Vk',
      showWeek: true,
      dateFormat: 'dd.mm.yy',
      firstDay: 1,
      isRTL: false,
      buttonImage: '/img/calendar.gif',
      showMonthAfterYear: false,
      yearSuffix: ''};

  $.datepicker.regional.sv = {
      closeText: 'Stäng',
      prevText: '&laquo;Förra',
      nextText: 'Nästa&raquo;',
      currentText: 'Idag',
      monthNames: ['Januari','Februari','Mars','April','Maj','Juni', 'Juli','Augusti','September','Oktober','November','December'],
      monthNamesShort: ['Jan','Feb','Mar','Apr','Maj','Jun', 'Jul','Aug','Sep','Okt','Nov','Dec'],
      dayNamesShort: ['Sön','Mån','Tis','Ons','Tor','Fre','Lör'],
      dayNames: ['Söndag','Måndag','Tisdag','Onsdag','Torsdag','Fredag','Lördag'],
      dayNamesMin: ['Sö','Må','Ti','On','To','Fr','Lö'],
      weekHeader: 'Ve',
      dateFormat: 'dd.mm.yy',
      firstDay: 1,
      isRTL: false,
      buttonImage: '/img/calendar.gif',
      showMonthAfterYear: false,
      yearSuffix: ''};

  $.datepicker.setDefaults($.datepicker.regional.fi);
});
