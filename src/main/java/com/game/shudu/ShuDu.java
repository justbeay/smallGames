package com.game.shudu;

import com.game.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by lee on 2017-08-14.
 */
public class ShuDu {

    private static final Integer DIRECTION_HORIZONTAL = 1;  // 水平方向
    private static final Integer DIRECTION_VERTICAL = 2;  // 垂直方向
    private static final Integer DIRECTION_RANGE = 4;  // 临近区间方向

    private static final char CHAR_BORDER = '#';
    private static final char CHAR_SEPARATOR_X = '-';
    private static final char CHAR_SEPARATOR_Y = '|';

    private static final int CELL_INNER_WIDTH = 3;

    private static Logger logger = LoggerFactory.getLogger(ShuDu.class);

    private int n;  // 数组规模 n*n
    private List<Integer> availableValues;  // 所有可能的数字集合，从小到大排列
    private Cell[] cells;  // 所有格子对象数组
    private int sumMatch;  // 数组一行或一列所有格子的数字和
    private int maxStepAllowed = -1;  // 最大允许步数，超过步数后不再遍历，直接开始新的尝试或退出
    private int maxRetryTimes = -1;  // 运行步数超出允许的最大步数后，可重试的次数

    public ShuDu(int n){
        if(n <= 0){
            throw new RuntimeException("n must be positive value");
        }
        logger.info("init a shuDu instance of {}*{}", n, n);
        this.n = n;
        this.initCells();
        this.initSumMatch();
    }

    private void initCells(){
        int width = n * n;
        // 初始化所有可能出现的值列表
        this.availableValues = new ArrayList<Integer>(width);
        for(int i=0; i<width; i++){
            this.availableValues.add(i + 1);
        }
        logger.info("available value could be one of {}", this.availableValues);
        // 初始化所有格子
        this.cells = new Cell[width * width];
        for(int i=0; i<width; i++){
            for(int j=0; j<width; j++){
                // 新建格子
                this.cells[i * width + j] = new Cell(i, j);
            }
        }
    }

    private void initSumMatch(){
        int sumMatch = 0;
        for(int i=0; i<this.availableValues.size(); i++){
            sumMatch += this.availableValues.get(i);
        }
        logger.info("a group of digit must have a sum: {}", sumMatch);
        this.sumMatch = sumMatch;
    }

    /**
     * 新增预设的固定值
     * @param xPos X坐标
     * @param yPos Y坐标
     * @param value 预设值
      */
    public void addFixedValue(int xPos, int yPos, int value){
        int scale = this.availableValues.size();
        boolean flag = false;
        if((1 <= xPos && xPos <= scale) && (1 <= yPos && yPos <= scale)){
            for(int i=0; i<this.availableValues.size(); i++){
                if(this.availableValues.get(i) == value){
                    flag = true;
                    break;
                }
            }
        }
        if(flag){  // 参数合法
            Cell curCell = this.cells[(xPos - 1) * scale + yPos - 1];
            curCell.setFixedValue(value);
        }
    }

    public void setMaxStepAllowed(int maxStepAllowed){
        this.maxStepAllowed = maxStepAllowed;
    }

    public void setMaxRetryTimes(int maxRetryTimes){
        this.maxRetryTimes = maxRetryTimes;
    }

    public boolean process(){
        logger.info("-------- start process...");
        long startIime = System.currentTimeMillis();
        boolean success = true;
        int retryTimes = 0;
        long stepCount = this.processLoop1();
        while(this.maxStepAllowed > 0 && stepCount >= this.maxStepAllowed) {
            if(++retryTimes > this.maxRetryTimes){
                success = false;
                logger.error("unable to finish processing with maxStepAllowed:{} and maxRetryTimes:{}", this.maxStepAllowed, this.maxRetryTimes);
                break;
            }
            logger.warn("unable to finish processing in maxStepAllowed({}), retry for the {}th time...", this.maxStepAllowed, retryTimes);
            this.reset();
            stepCount = this.processLoop1();
        }
        long endTime = System.currentTimeMillis();
        logger.info("-------- finish process {}, {} steps, use time:{}ms...",
                    success ? "success" : "failed",
                    retryTimes == 0 ? stepCount : String.format("%d*%d+%d=%d",
                            this.maxStepAllowed, Math.min(retryTimes, this.maxRetryTimes), stepCount,
                            this.maxStepAllowed * Math.min(retryTimes, this.maxRetryTimes) + stepCount),
                    endTime - startIime);
        return success;
    }

    /**
     * 循环处理数度
     * @return 循环处理格子次数
     */
    private long processLoop(){
        long loopCount = 0;
        for(int i=0; i<this.cells.length; ){
            Cell curCell = this.cells[i];
            logger.debug("process cell[{}, {}]...", curCell.x, curCell.y);

            if(!curCell.isFixed && !curCell.isProceed){
                List<Integer> validList = this.getValidValueList(curCell);
                curCell.setValidList(validList);
                curCell.isProceed = true;
            }
            // 判断当前格子是否是否还有可能的值
            if(!curCell.isFixed && !curCell.pickNextValidValue()){
                if(i == 0) {
                    throw new RuntimeException("no possible values available for given conditions...");
                }
                logger.debug("cell[{}, {}] has no possible value now, back one step...", curCell.x, curCell.y);
                curCell.clear();
                i --;
            }else{
                i ++;
            }
            // 超出允许运行最大步数后不管有无结束，均退出
            if(++loopCount >= this.maxStepAllowed && this.maxStepAllowed > 0) break;
            if(loopCount % 1000000 == 0) {
                logger.warn("{} steps was used", loopCount);
            }
        }
        return loopCount;
    }

    /**
     * 循环处理数度 (从最容易猜出值的地方开始入手)
     * @return
     */
    private long processLoop1(){
        long loopCount = 0;
        Deque<Cell> fillList = new LinkedList<Cell>();
        while(true) {
            Cell curCell = this.getNextUncertainCell();
            if(curCell == null) {
                break;
            }
            fillList.push(curCell);
            if(!curCell.isProceed) {
                List<Integer> validList = this.getValidValueList(curCell);
                curCell.setValidList(validList);
                curCell.isProceed = true;
            }
            while(true){
                Cell cell = fillList.peek();
                if(cell.pickNextValidValue()) {
                   break;
                }
                cell.clear();
                fillList.pop();
                if(fillList.isEmpty()) {
                    throw new RuntimeException("no possible values available for given conditions...");
                }
            }
            // 超出允许运行最大步数后不管有无结束，均退出
            if(++loopCount >= this.maxStepAllowed && this.maxStepAllowed > 0) break;
            if(loopCount % 10000 == 0) {
                logger.warn("{} steps was used", loopCount);
            }
        }
        return loopCount;
    }

    /**
     * found cell which has the largest possibility (less uncertain cells in 3 direction) to figure out the cell value
     */
    private Cell getNextUncertainCell() {
        Cell retCell = null;
        int maxKnownCnt = -1;
        for(Cell cell : this.cells) {
            if (!cell.isFixed && !cell.isProceed) {
                int knownCnt = 0;
                for (Cell perCell : this.getRelatedCells(cell, DIRECTION_HORIZONTAL)) {
                    if (perCell.isFixed || perCell.isProceed) knownCnt++;
                }
                if (knownCnt > maxKnownCnt) {
                    maxKnownCnt = knownCnt;
                    retCell = cell;
                }
                knownCnt = 0;
                for (Cell perCell : this.getRelatedCells(cell, DIRECTION_VERTICAL)) {
                    if (perCell.isFixed || perCell.isProceed) knownCnt++;
                }
                if (knownCnt > maxKnownCnt) {
                    maxKnownCnt = knownCnt;
                    retCell = cell;
                }
                knownCnt = 0;
                for (Cell perCell : this.getRelatedCells(cell, DIRECTION_RANGE)) {
                    if (perCell.isFixed || perCell.isProceed) knownCnt++;
                }
                if (knownCnt > maxKnownCnt) {
                    maxKnownCnt = knownCnt;
                    retCell = cell;
                }
            }
        }
        return retCell;
    }

    /**
     * 获取格子上所有可能的值
     * @param cell
     * @return
     */
    private List<Integer> getValidValueList(Cell cell) {
        int horizontalSum = 0;
        int verticalSum = 0;
        int rangeSum = 0;
        List<Integer> horizontalValues = new ArrayList<Integer>();
        List<Integer> verticalValues = new ArrayList<Integer>();
        List<Integer> rangeValues = new ArrayList<Integer>();
        for(Cell percell : this.cells){
            if(!percell.isProceed) continue;
            if(this.isRelatedCell(cell, percell, DIRECTION_HORIZONTAL)){
                horizontalValues.add(percell.getValue());
                horizontalSum += percell.getValue();
            }
            if(this.isRelatedCell(cell, percell, DIRECTION_VERTICAL)){
                verticalValues.add(percell.getValue());
                verticalSum += percell.getValue();
            }
            if(this.isRelatedCell(cell, percell, DIRECTION_RANGE)){
                rangeValues.add(percell.getValue());
                rangeSum += percell.getValue();
            }
        }
        // 开始计算
        int minHorizontalDelta = this.getMinDeltaInDirection(horizontalValues);
        int minVerticalDalta = this.getMinDeltaInDirection(verticalValues);
        int minRangeDelta = this.getMinDeltaInDirection(rangeValues);
        List<Integer> validValues = new ArrayList<Integer>();
        for(int i=0; i<this.availableValues.size(); i++){
            int availableValue = this.availableValues.get(i);
            if(horizontalSum + minHorizontalDelta > this.sumMatch || verticalSum + minVerticalDalta > this.sumMatch || rangeSum + minRangeDelta > this.sumMatch){
                break;  // 当前累计和大于要求的累积和，退出循环
            }
            if(!horizontalValues.contains(availableValue) && !verticalValues.contains(availableValue) && !rangeValues.contains(availableValue)){
                // 小于要求的累计和，且数字未出现在三种对应的格子集合中
                validValues.add(availableValue);
            }
        }
        return validValues;
    }

    private boolean isRelatedCell(Cell left, Cell right, int direction) {
        if(direction == DIRECTION_HORIZONTAL) {
            return left.x == right.x;
        } else if(direction == DIRECTION_VERTICAL) {
            return left.y == right.y;
        } else if(direction == DIRECTION_RANGE) {
            return left.x / this.n == right.x / this.n && left.y / this.n == right.y / this.n;
        }
        return false;
    }

    private List<Cell> getRelatedCells(Cell cell, int direction) {
        List<Cell> results = new ArrayList<Cell>();
        for(Cell perCell : this.cells) {
            if(this.isRelatedCell(cell, perCell, direction)) {
                results.add(perCell);
            }
        }
        return results;
    }

    private int getMinDeltaInDirection(List<Integer> directionValues){
        if(directionValues.size() == this.availableValues.size()){
            return 0;
        }else{
            for(Integer availableValue : this.availableValues){
                if(!directionValues.contains(availableValue)){
                    return availableValue * (this.availableValues.size() - directionValues.size());
                }
            }
            return this.availableValues.size() - directionValues.size();
        }
    }

    private void reset(){
        for(Cell cell : this.cells){
            if(!cell.isFixed && cell.isProceed){
                cell.clear();
            }
        }
    }

    public boolean validation(boolean allowEmpty){
        logger.info("start validation...");
        boolean checkResult = true;
        for(Cell cell : this.cells){
            if(cell.getValue() == null) return false;
            if(checkResult && cell.y == 0){  // 需要检查水平方向
                checkResult = this.checkCellRelated(cell, DIRECTION_HORIZONTAL, allowEmpty);
            }
            if(checkResult && cell.x == 0){  // 需要检查垂直方向
                checkResult = this.checkCellRelated(cell, DIRECTION_VERTICAL, allowEmpty);
            }
            if(checkResult && cell.x % n == 0 && cell.y % n == 0){  // 需要检查附件区间
                checkResult = this.checkCellRelated(cell, DIRECTION_RANGE, allowEmpty);
            }
        }
        logger.info("validation finished, result:{}", checkResult ? "OK" : "ERROR");
        return checkResult;
    }

    /**
     * 检查指定格子在某一方向上的所有关联格子
     * @param cellCheck 待检查格子信息
     * @param direction 检查方向（HORIZONTAL、VERTICAL、RANGE）
     * @param allowEmpty 是否允许空格子
     * @return
     */
    private boolean checkCellRelated(Cell cellCheck, int direction, boolean allowEmpty){
        logger.debug("check cell[{}, {}], direction is: {}...", cellCheck.x, cellCheck.y, direction);
        int sum = 0;
        List<Integer> values = new ArrayList<Integer>();
        for(Cell cell : this.cells){
            boolean isRelated = false;  // 是否与被检查格子相关
            if(DIRECTION_HORIZONTAL == direction){
                isRelated = cellCheck.x == cell.x;
            }else if(DIRECTION_VERTICAL == direction){
                isRelated = cellCheck.y == cell.y;
            }else if(DIRECTION_RANGE == direction){
                isRelated = cellCheck.x / n == cell.x / n && cellCheck.y / n == cell.y / n;
            }
            if(isRelated && cellCheck.getValue() != null && cell.getValue() != null){
                sum += cell.getValue();
                if(values.contains(cell.getValue())){  // 相关的格子有重复数字
                    return false;
                }
                values.add(cell.getValue());
            }
        }
        if(!allowEmpty){
            return values.size() == this.availableValues.size() && sum == this.sumMatch;
        }
        return values.size() == this.availableValues.size()
                ? sum == this.sumMatch
                : sum <= this.sumMatch - this.availableValues.size() + values.size();
    }

    /**
     * 获取数度格式化输出结果
     */
    public String getFormatResult(){
        int numPrintWidth = CELL_INNER_WIDTH;
        int scale = this.availableValues.size();  // 数字矩阵规模
        int width = scale * (numPrintWidth + 1) + 1;   // 待打印字符宽度
        StringBuilder resultStr = new StringBuilder(width * width);  // 待打印字符串
        // 构造待打印边界线
        StringBuilder outerBorderLine = new StringBuilder(width);  // 外部边界线
        StringBuilder innerBorderLine = new StringBuilder(width);  // 内部边界线
        for(int i=0; i<scale; i++){
            if(i == 0){
                outerBorderLine.append(CHAR_BORDER);
                innerBorderLine.append(CHAR_BORDER);
            }
            outerBorderLine.append(StringUtil.printCharTimes(CHAR_BORDER, numPrintWidth + 1));
            innerBorderLine.append(StringUtil.printCharTimes(CHAR_SEPARATOR_X, numPrintWidth))
                    .append((i + 1) % n == 0 ? CHAR_BORDER : CHAR_SEPARATOR_Y);
        }
        for(int i=0; i<scale; i++){
            // 构造带数字的打印行
            StringBuilder digitLine = new StringBuilder(width);
            for(int j=0; j<scale; j++){
                Cell cell = this.cells[i * scale + j];
                if(j == 0) digitLine.append(CHAR_BORDER);
                digitLine.append(StringUtil.printNumber(cell.getValue(), numPrintWidth))
                        .append((j + 1) % n == 0 ? CHAR_BORDER : CHAR_SEPARATOR_Y);
            }
            // 添加到打印结果串
            resultStr.append(i % n == 0  ? outerBorderLine : innerBorderLine).append("\n");
            resultStr.append(digitLine).append("\n");
        }
        resultStr.append(outerBorderLine).append("\n");
        logger.debug("format result is:\n{}", resultStr);
        return resultStr.toString();
    }
}
